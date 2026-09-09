package com.example.beetle.application.service

import com.example.beetle.application.port.RegisterTransactionCommand
import com.example.beetle.application.port.TransactionSearchQuery
import com.example.beetle.application.port.UpdateTransactionCommand
import com.example.beetle.domain.exception.DomainStateException
import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethod
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType
import com.example.beetle.domain.model.Transaction
import com.example.beetle.domain.model.TransactionId
import com.example.beetle.domain.repository.CategoryRepository
import com.example.beetle.domain.repository.PaymentMethodRepository
import com.example.beetle.domain.repository.TransactionRepository
import com.example.beetle.domain.service.BillDateCalculator
import com.example.beetle.fixture.bankAccount
import com.example.beetle.fixture.cash
import com.example.beetle.fixture.creditCard
import com.example.beetle.fixture.expenseCategory
import com.example.beetle.fixture.installmentTransaction
import com.example.beetle.fixture.transaction
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate

@DisplayName("TransactionService 유스케이스")
class TransactionServiceTest {

    private val transactionRepository = mockk<TransactionRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val paymentMethodRepository = mockk<PaymentMethodRepository>()

    // 도메인 서비스는 목이 아닌 실제 구현을 쓴다. 청구일 산출은 검증 대상 로직이다.
    private val transactionService = TransactionService(
        transactionRepository,
        categoryRepository,
        paymentMethodRepository,
        BillDateCalculator(),
    )

    private val 식비 = expenseCategory(name = "식비", id = 1L)
    private val 삼성카드 = creditCard(name = "삼성카드", paymentDay = 14, id = 1L)

    @BeforeEach
    fun setUpMasterData() {
        every { categoryRepository.findById(CategoryId(1L)) } returns 식비
        every { paymentMethodRepository.findById(PaymentMethodId(1L)) } returns 삼성카드
    }

    @Nested
    @DisplayName("등록 - 청구일 자동 산출")
    inner class Register {

        @Test
        fun `신용카드 거래는 청구일이 결제 조건으로부터 자동 산출된다`() {
            // given
            val saved = slot<Transaction>()
            every { transactionRepository.save(capture(saved)) } answers {
                saved.captured.assignId(TransactionId(1L))
            }

            // when
            transactionService.register(
                RegisterTransactionCommand(
                    categoryId = CategoryId(1L),
                    paymentMethodId = PaymentMethodId(1L),
                    amount = Money.of(45_000),
                    spentDate = LocalDate.of(2026, 1, 10),
                    memo = "이마트",
                ),
            )

            // then: 결제일 14일 카드로 1월 소비 -> 2월 14일 청구
            assertThat(saved.captured.spentDate).isEqualTo(LocalDate.of(2026, 1, 10))
            assertThat(saved.captured.billDate).isEqualTo(LocalDate.of(2026, 2, 14))
            assertThat(saved.captured.memo).isEqualTo("이마트")
        }

        @Test
        fun `현금 거래는 청구일이 소비일과 같다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(2L)) } returns cash(id = 2L)
            val saved = slot<Transaction>()
            every { transactionRepository.save(capture(saved)) } answers {
                saved.captured.assignId(TransactionId(1L))
            }

            // when
            transactionService.register(
                RegisterTransactionCommand(
                    categoryId = CategoryId(1L),
                    paymentMethodId = PaymentMethodId(2L),
                    amount = Money.of(5_000),
                    spentDate = LocalDate.of(2026, 1, 10),
                ),
            )

            // then
            assertThat(saved.captured.billDate).isEqualTo(LocalDate.of(2026, 1, 10))
        }

        @ParameterizedTest
        @EnumSource(PaymentMethodType::class, names = ["CHECK_CARD", "BANK_ACCOUNT", "CASH"])
        fun `즉시 결제 수단은 출금 완료 여부를 지정하지 않으면 정산 완료로 등록된다`(
            type: PaymentMethodType,
        ) {
            // given: 현금·체크카드·계좌는 소비 시점에 이미 돈이 나갔다.
            // 미정산으로 남으면 이미 나간 돈이 "청구 예정액" 에 잡힌다.
            every { paymentMethodRepository.findById(PaymentMethodId(2L)) } returns
                PaymentMethod.create(type.name, type).assignId(PaymentMethodId(2L))
            val saved = slot<Transaction>()
            every { transactionRepository.save(capture(saved)) } answers {
                saved.captured.assignId(TransactionId(1L))
            }

            // when
            transactionService.register(
                RegisterTransactionCommand(
                    categoryId = CategoryId(1L),
                    paymentMethodId = PaymentMethodId(2L),
                    amount = Money.of(7_000),
                    spentDate = LocalDate.of(2026, 1, 14),
                ),
            )

            // then
            assertThat(saved.captured.isSettled).isTrue()
            assertThat(saved.captured.billDate).isEqualTo(saved.captured.spentDate)
        }

        @Test
        fun `신용카드는 출금 완료 여부를 지정하지 않으면 미정산으로 등록된다`() {
            // given: 청구일에 출금되므로 등록 시점에는 아직 나가지 않은 돈이다
            val saved = slot<Transaction>()
            every { transactionRepository.save(capture(saved)) } answers {
                saved.captured.assignId(TransactionId(1L))
            }

            // when
            transactionService.register(
                RegisterTransactionCommand(
                    categoryId = CategoryId(1L),
                    paymentMethodId = PaymentMethodId(1L),
                    amount = Money.of(45_000),
                    spentDate = LocalDate.of(2026, 1, 10),
                ),
            )

            // then
            assertThat(saved.captured.isSettled).isFalse()
        }

        @Test
        fun `즉시 결제 수단도 미정산을 명시하면 그 값을 따른다`() {
            // given: 미래 날짜의 계좌 자동이체처럼 아직 나가지 않은 경우
            every { paymentMethodRepository.findById(PaymentMethodId(2L)) } returns
                bankAccount(id = 2L)
            val saved = slot<Transaction>()
            every { transactionRepository.save(capture(saved)) } answers {
                saved.captured.assignId(TransactionId(1L))
            }

            // when
            transactionService.register(
                RegisterTransactionCommand(
                    categoryId = CategoryId(1L),
                    paymentMethodId = PaymentMethodId(2L),
                    amount = Money.of(750_000),
                    spentDate = LocalDate.of(2026, 3, 5),
                    isSettled = false,
                ),
            )

            // then
            assertThat(saved.captured.isSettled).isFalse()
        }

        @Test
        fun `신용카드도 정산 완료를 명시하면 그 값을 따른다`() {
            // given: 과거 거래를 소급 입력하는 경우
            val saved = slot<Transaction>()
            every { transactionRepository.save(capture(saved)) } answers {
                saved.captured.assignId(TransactionId(1L))
            }

            // when
            transactionService.register(
                RegisterTransactionCommand(
                    categoryId = CategoryId(1L),
                    paymentMethodId = PaymentMethodId(1L),
                    amount = Money.of(45_000),
                    spentDate = LocalDate.of(2025, 11, 10),
                    isSettled = true,
                ),
            )

            // then
            assertThat(saved.captured.isSettled).isTrue()
        }

        @Test
        fun `청구일을 직접 지정하면 자동 산출을 덮어쓴다`() {
            // given: 카드사 사정으로 청구일이 예외적으로 달라진 경우
            val saved = slot<Transaction>()
            every { transactionRepository.save(capture(saved)) } answers {
                saved.captured.assignId(TransactionId(1L))
            }

            // when
            transactionService.register(
                RegisterTransactionCommand(
                    categoryId = CategoryId(1L),
                    paymentMethodId = PaymentMethodId(1L),
                    amount = Money.of(10_000),
                    spentDate = LocalDate.of(2026, 1, 10),
                    billDate = LocalDate.of(2026, 3, 20),
                ),
            )

            // then
            assertThat(saved.captured.billDate).isEqualTo(LocalDate.of(2026, 3, 20))
        }

        @Test
        fun `통계 제외 거래로 등록할 수 있다`() {
            // given: 회사가 전액 지원하는 통신비
            val saved = slot<Transaction>()
            every { transactionRepository.save(capture(saved)) } answers {
                saved.captured.assignId(TransactionId(1L))
            }

            // when
            transactionService.register(
                RegisterTransactionCommand(
                    categoryId = CategoryId(1L),
                    paymentMethodId = PaymentMethodId(1L),
                    amount = Money.of(50_000),
                    spentDate = LocalDate.of(2026, 1, 10),
                    isExcludedFromStats = true,
                ),
            )

            // then
            assertThat(saved.captured.isExcludedFromStats).isTrue()
        }

        @Test
        fun `존재하지 않는 카테고리로는 등록할 수 없다`() {
            // given
            every { categoryRepository.findById(CategoryId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy {
                    transactionService.register(
                        RegisterTransactionCommand(
                            categoryId = CategoryId(99L),
                            paymentMethodId = PaymentMethodId(1L),
                            amount = Money.of(10_000),
                            spentDate = LocalDate.of(2026, 1, 10),
                        ),
                    )
                }
                .withMessageContaining("카테고리")

            verify(exactly = 0) { transactionRepository.save(any()) }
        }

        @Test
        fun `존재하지 않는 결제 수단으로는 등록할 수 없다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy {
                    transactionService.register(
                        RegisterTransactionCommand(
                            categoryId = CategoryId(1L),
                            paymentMethodId = PaymentMethodId(99L),
                            amount = Money.of(10_000),
                            spentDate = LocalDate.of(2026, 1, 10),
                        ),
                    )
                }
                .withMessageContaining("결제 수단")

            verify(exactly = 0) { transactionRepository.save(any()) }
        }

        @Test
        fun `수동 지정한 청구일이 소비일보다 앞서면 도메인이 거부한다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    transactionService.register(
                        RegisterTransactionCommand(
                            categoryId = CategoryId(1L),
                            paymentMethodId = PaymentMethodId(1L),
                            amount = Money.of(10_000),
                            spentDate = LocalDate.of(2026, 2, 10),
                            billDate = LocalDate.of(2026, 1, 10),
                        ),
                    )
                }

            verify(exactly = 0) { transactionRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("수정")
    inner class Update {

        @BeforeEach
        fun stubSave() {
            every { transactionRepository.save(any()) } answers { firstArg() }
        }

        @Test
        fun `금액을 정정한다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns
                transaction(amount = 10_000L, id = 1L)

            // when
            val result = transactionService.update(
                UpdateTransactionCommand(TransactionId(1L), amount = Money.of(12_000)),
            )

            // then
            assertThat(result.amount).isEqualTo(Money.of(12_000))
        }

        @Test
        fun `소비일을 바꾸면 청구일도 다시 산출된다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns
                transaction(
                    spentDate = LocalDate.of(2026, 1, 10),
                    billDate = LocalDate.of(2026, 2, 14),
                    id = 1L,
                )

            // when: 3월 소비로 변경
            val result = transactionService.update(
                UpdateTransactionCommand(
                    TransactionId(1L), spentDate = LocalDate.of(2026, 3, 5),
                ),
            )

            // then: 결제일 14일 카드 -> 4월 14일 청구
            assertThat(result.spentDate).isEqualTo(LocalDate.of(2026, 3, 5))
            assertThat(result.billDate).isEqualTo(LocalDate.of(2026, 4, 14))
        }

        @Test
        fun `카테고리를 변경할 때 존재 여부를 확인한다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns transaction(id = 1L)
            every { categoryRepository.findById(CategoryId(5L)) } returns expenseCategory(id = 5L)

            // when
            val result = transactionService.update(
                UpdateTransactionCommand(TransactionId(1L), categoryId = CategoryId(5L)),
            )

            // then
            assertThat(result.categoryId).isEqualTo(CategoryId(5L))
        }

        @Test
        fun `존재하지 않는 카테고리로는 변경할 수 없다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns transaction(id = 1L)
            every { categoryRepository.findById(CategoryId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy {
                    transactionService.update(
                        UpdateTransactionCommand(TransactionId(1L), categoryId = CategoryId(99L)),
                    )
                }

            verify(exactly = 0) { transactionRepository.save(any()) }
        }

        @Test
        fun `메모를 변경한다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns
                transaction(memo = "이전", id = 1L)

            // when
            val result = transactionService.update(
                UpdateTransactionCommand(TransactionId(1L), memo = "이후"),
            )

            // then
            assertThat(result.memo).isEqualTo("이후")
        }

        @Test
        fun `clearMemo 가 참이면 메모를 삭제한다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns
                transaction(memo = "이전", id = 1L)

            // when
            val result = transactionService.update(
                UpdateTransactionCommand(TransactionId(1L), memo = "무시됨", clearMemo = true),
            )

            // then
            assertThat(result.memo).isNull()
        }

        @Test
        fun `여러 필드를 한 번에 수정한다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns
                transaction(amount = 10_000L, memo = "이전", id = 1L)
            every { categoryRepository.findById(CategoryId(5L)) } returns expenseCategory(id = 5L)

            // when
            val result = transactionService.update(
                UpdateTransactionCommand(
                    id = TransactionId(1L),
                    categoryId = CategoryId(5L),
                    amount = Money.of(30_000),
                    memo = "이후",
                    spentDate = LocalDate.of(2026, 2, 20),
                ),
            )

            // then
            assertThat(result.categoryId).isEqualTo(CategoryId(5L))
            assertThat(result.amount).isEqualTo(Money.of(30_000))
            assertThat(result.memo).isEqualTo("이후")
            assertThat(result.billDate).isEqualTo(LocalDate.of(2026, 3, 14))
        }

        @Test
        fun `변경 사항이 없으면 기존 상태를 그대로 저장한다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns
                transaction(amount = 10_000L, id = 1L)

            // when
            val result = transactionService.update(UpdateTransactionCommand(TransactionId(1L)))

            // then
            assertThat(result.amount).isEqualTo(Money.of(10_000))
        }

        @Test
        fun `결제 완료된 거래의 금액 정정은 도메인이 거부한다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns
                transaction(isSettled = true, id = 1L)

            // when & then
            assertThatExceptionOfType(DomainStateException::class.java)
                .isThrownBy {
                    transactionService.update(
                        UpdateTransactionCommand(TransactionId(1L), amount = Money.of(1_000)),
                    )
                }
        }

        @Test
        fun `존재하지 않는 거래는 수정할 수 없다`() {
            // given
            every { transactionRepository.findById(TransactionId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy {
                    transactionService.update(UpdateTransactionCommand(TransactionId(99L)))
                }
                .withMessageContaining("거래 내역")
        }
    }

    @Nested
    @DisplayName("결제 완료 처리와 통계 제외")
    inner class SettlementAndExclusion {

        @BeforeEach
        fun stubSave() {
            every { transactionRepository.save(any()) } answers { firstArg() }
        }

        @Test
        fun `결제 완료로 표시한다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns
                transaction(isSettled = false, id = 1L)

            // when & then
            assertThat(transactionService.settle(TransactionId(1L)).isSettled).isTrue()
        }

        @Test
        fun `이미 결제 완료된 거래를 다시 완료하면 도메인이 거부한다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns
                transaction(isSettled = true, id = 1L)

            // when & then
            assertThatExceptionOfType(DomainStateException::class.java)
                .isThrownBy { transactionService.settle(TransactionId(1L)) }
        }

        @Test
        fun `결제 완료를 되돌린다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns
                transaction(isSettled = true, id = 1L)

            // when & then
            assertThat(transactionService.unsettle(TransactionId(1L)).isSettled).isFalse()
        }

        @Test
        fun `결제 완료되지 않은 거래를 되돌리면 도메인이 거부한다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns
                transaction(isSettled = false, id = 1L)

            // when & then
            assertThatExceptionOfType(DomainStateException::class.java)
                .isThrownBy { transactionService.unsettle(TransactionId(1L)) }
        }

        @Test
        fun `통계에서 제외한다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns transaction(id = 1L)

            // when & then
            assertThat(
                transactionService.changeStatsExclusion(TransactionId(1L), excluded = true)
                    .isExcludedFromStats,
            ).isTrue()
        }

        @Test
        fun `통계 제외를 해제한다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns
                transaction(isExcludedFromStats = true, id = 1L)

            // when & then
            assertThat(
                transactionService.changeStatsExclusion(TransactionId(1L), excluded = false)
                    .isExcludedFromStats,
            ).isFalse()
        }
    }

    @Nested
    @DisplayName("조회 - 기준일 축 전환 (PRD 2-①)")
    inner class Search {

        @Test
        fun `소비일 기준으로 조회한다`() {
            // given
            every {
                transactionRepository.findAllByPeriod(
                    DateBasis.SPENT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, null,
                )
            } returns listOf(transaction(id = 1L))

            // when
            val result = transactionService.search(
                TransactionSearchQuery(
                    DateBasis.SPENT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                ),
            )

            // then
            assertThat(result).hasSize(1)
        }

        @Test
        fun `청구일 기준으로 조회한다`() {
            // given
            every {
                transactionRepository.findAllByPeriod(
                    DateBasis.BILL, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), null, null,
                )
            } returns listOf(transaction(id = 1L), transaction(id = 2L))

            // when
            val result = transactionService.search(
                TransactionSearchQuery(
                    DateBasis.BILL, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                ),
            )

            // then
            assertThat(result).hasSize(2)
        }

        @Test
        fun `카테고리와 결제 수단으로 필터링한다`() {
            // given
            every {
                transactionRepository.findAllByPeriod(
                    DateBasis.SPENT,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 31),
                    CategoryId(1L),
                    PaymentMethodId(1L),
                )
            } returns listOf(transaction(id = 1L))

            // when
            val result = transactionService.search(
                TransactionSearchQuery(
                    basis = DateBasis.SPENT,
                    from = LocalDate.of(2026, 1, 1),
                    to = LocalDate.of(2026, 1, 31),
                    categoryId = CategoryId(1L),
                    paymentMethodId = PaymentMethodId(1L),
                ),
            )

            // then
            assertThat(result).hasSize(1)
        }

        @Test
        fun `시작일이 종료일보다 늦으면 조회할 수 없다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    transactionService.search(
                        TransactionSearchQuery(
                            DateBasis.SPENT, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1),
                        ),
                    )
                }
                .withMessageContaining("조회 시작일이 종료일보다 늦습니다")

            // 값 객체 파라미터에는 any() 를 쓰지 않는다. MockK 가 난수로 값 객체를 만들면서
            // ID 양수 불변식을 위반해 간헐적으로 실패한다 (CLAUDE.md 5.4).
            // 리포지토리가 전혀 호출되지 않았음을 confirmVerified 로 결정적으로 검증한다.
            confirmVerified(transactionRepository)
        }

        @Test
        fun `시작일과 종료일이 같은 하루 조회는 허용된다`() {
            // given
            val day = LocalDate.of(2026, 1, 10)
            every {
                transactionRepository.findAllByPeriod(DateBasis.SPENT, day, day, null, null)
            } returns emptyList()

            // when & then
            assertThat(transactionService.search(TransactionSearchQuery(DateBasis.SPENT, day, day)))
                .isEmpty()
        }
    }

    @Nested
    @DisplayName("단건 조회와 삭제")
    inner class GetAndDelete {

        @Test
        fun `식별자로 조회한다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns transaction(id = 1L)

            // when & then
            assertThat(transactionService.getById(TransactionId(1L)).id)
                .isEqualTo(TransactionId(1L))
        }

        @Test
        fun `존재하지 않으면 404 로 처리한다`() {
            // given
            every { transactionRepository.findById(TransactionId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy { transactionService.getById(TransactionId(99L)) }
        }

        @Test
        fun `거래를 삭제한다`() {
            // given
            every { transactionRepository.findById(TransactionId(1L)) } returns transaction(id = 1L)
            every { transactionRepository.deleteById(TransactionId(1L)) } returns Unit

            // when
            transactionService.delete(TransactionId(1L))

            // then
            verify(exactly = 1) { transactionRepository.deleteById(TransactionId(1L)) }
        }

        @Test
        fun `할부 회차 거래는 개별 삭제할 수 없다`() {
            // given: 회차 하나만 지우면 "회차 금액의 합 == 총액" 이 깨진다.
            // 계획 단위로 해지해야 한다
            every { transactionRepository.findById(TransactionId(5L)) } returns
                installmentTransaction(sequence = 2, id = 5L)

            // when & then
            assertThatExceptionOfType(DomainStateException::class.java)
                .isThrownBy { transactionService.delete(TransactionId(5L)) }
                .withMessageContaining("할부 계획을 해지하십시오")

            verify(exactly = 0) { transactionRepository.deleteById(TransactionId(5L)) }
        }

        @Test
        fun `존재하지 않는 거래 삭제는 404 로 처리한다`() {
            // given
            every { transactionRepository.findById(TransactionId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy { transactionService.delete(TransactionId(99L)) }
        }
    }
}

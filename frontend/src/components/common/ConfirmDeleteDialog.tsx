import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { FieldError } from './FieldError'

export interface ConfirmDeleteDialogProps<T extends { name: string }> {
  /** 삭제 대상. `null` 이면 닫힌 상태다. */
  target: T | null
  /** 서버가 거부한 이유. 참조가 남아 있으면 삭제할 수 없다. */
  error?: string | null
  pending?: boolean
  /** 대상 종류를 설명하는 문구. 예: `카테고리` */
  noun?: string
  onCancel: () => void
  onConfirm: (target: T) => void
}

/**
 * 삭제 확인 모달.
 *
 * 삭제는 물리 삭제이고 되돌릴 수 없으므로 한 단계를 둔다. 서버가 거부한 경우
 * (거래가 남아 있는 카테고리 등) 그 이유를 그대로 보여준다. 백엔드가 원인을 알 수 있는
 * 메시지를 이미 만들어 주므로 프론트에서 다시 지어내지 않는다.
 */
export function ConfirmDeleteDialog<T extends { name: string }>({
  target,
  error,
  pending = false,
  noun = '항목',
  onCancel,
  onConfirm,
}: ConfirmDeleteDialogProps<T>) {
  if (target === null) return null

  return (
    <Dialog open onOpenChange={(next) => !next && onCancel()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{noun} 삭제</DialogTitle>
          <DialogDescription>
            <strong>{target.name}</strong> 을(를) 삭제합니다. 되돌릴 수 없습니다.
          </DialogDescription>
        </DialogHeader>

        {error && <FieldError message={error} />}

        <DialogFooter>
          <Button variant="outline" onClick={onCancel}>
            취소
          </Button>
          <Button variant="destructive" disabled={pending} onClick={() => onConfirm(target)}>
            {pending ? '삭제 중…' : '삭제'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

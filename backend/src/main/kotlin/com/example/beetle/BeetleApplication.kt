package com.example.beetle

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BeetleApplication

fun main(args: Array<String>) {
	runApplication<BeetleApplication>(*args)
}

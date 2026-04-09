package com.stockproject.market

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MarketSvcApplication

fun main(args: Array<String>) {
    runApplication<MarketSvcApplication>(*args)
}

package io.github.tafilovic.publishtest

import android.support.annotation.Keep
import android.support.annotation.NonNull

@Keep
class Tester {
    fun print() {
        print("Hello world!!!")
    }

    @NonNull
    fun getMessage(): String {
        return "Hello World!!"
    }
}
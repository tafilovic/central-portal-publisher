package io.github.tafilovic.publishtest

import android.support.annotation.NonNull
import androidx.annotation.Keep


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
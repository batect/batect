package decompose.testutils

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has

fun withMessage(message: String): Matcher<Throwable> {
    return has(Throwable::message, equalTo(message))
}

/*
 * Copyright © 2023 GlobalMentor, Inc. <https://www.globalmentor.com/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.flange.cloud.aws;

import static com.globalmentor.java.Throwables.*;
import static dev.flange.cloud.aws.AwsLambda.UnhandledError;
import static java.nio.charset.StandardCharsets.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import javax.annotation.*;

import org.junit.jupiter.api.*;

import com.globalmentor.java.StackTrace;

import dev.flange.cloud.UnavailableMarshalledThrowable;

/**
 * Tests of {@link AwsLambda}.
 * @author Garret Wilson
 */
public class AwsLambdaTest {

	/**
	 * Example AWS Lambda response payload for an unhandled {@link IllegalArgumentException}.
	 * @see software.amazon.awssdk.services.lambda.model.InvokeResponse.InvokeResponse#payload()
	 */
	static final String EXAMPLE_UNHANDLED_ILLEGAL_ARGUMENT_EXCEPTION_RESPONSE_PAYLOAD = """
			{
			  "errorMessage": "test illegal argument exception from user service",
			  "errorType": "java.lang.IllegalArgumentException",
			  "stackTrace": [
			    "dev.flange.example.cloud.hellouser_faas.service.user.impl.UserServiceImpl.findUserProfileByUsername(UserServiceImpl.java:45)",
			    "java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)",
			    "java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(Unknown Source)",
			    "java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(Unknown Source)",
			    "java.base/java.lang.reflect.Method.invoke(Unknown Source)",
			    "dev.flange.cloud.aws.AbstractAwsCloudFunctionServiceHandler.invokeService(AbstractAwsCloudFunctionServiceHandler.java:168)",
			    "dev.flange.cloud.aws.AbstractAwsCloudFunctionServiceHandler.handleRequest(AbstractAwsCloudFunctionServiceHandler.java:105)",
			    "java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)",
			    "java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(Unknown Source)",
			    "java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(Unknown Source)",
			    "java.base/java.lang.reflect.Method.invoke(Unknown Source)"
			  ]
			}
			""";

	/**
	 * Example AWS Lambda response payload for an unhandled {@link IOException} with an {@link IllegalArgumentException} cause.
	 * @see software.amazon.awssdk.services.lambda.model.InvokeResponse.InvokeResponse#payload()
	 */
	static final String EXAMPLE_UNHANDLED_IO_EXCEPTION_WITH_ILLEGAL_ARGUMENT_EXCEPTION_CAUSE_RESPONSE_PAYLOAD = """
			{
			  "errorMessage": "I/O problem",
			  "errorType": "java.io.IOException",
			  "stackTrace": [
			    "com.Example.foo(FooBar.java:789)",
			    "com.Example.foo(FooBar.java:456)"
			  ],
			  "cause": {
			    "errorMessage": "bad input",
			    "errorType": "java.lang.IllegalArgumentException",
			    "stackTrace": [
			      "com.Example.foo(FooBar.java:123)"
			    ]
			  }
			}
			""";

	/**
	 * Example AWS Lambda response payload for an unhandled {@link RuntimeException}, with two levels of nested causes and only a single (truncated) stack trace
	 * in the deepest cause.
	 * @apiNote This example is modified from <a href="https://docs.aws.amazon.com/lambda/latest/dg/java-exceptions.html">AWS Lambda function errors in Java</a>.
	 * @see software.amazon.awssdk.services.lambda.model.InvokeResponse.InvokeResponse#payload()
	 */
	static final String EXAMPLE_UNHANDLED_RUNTIME_EXCEPTION_TWO_CAUSE_LEVELS_RESPONSE_PAYLOAD = """
			{
			  "errorMessage": "An error occurred during JSON parsing",
			  "errorType": "java.lang.RuntimeException",
			  "stackTrace": [],
			  "cause": {
			    "errorMessage": "java.lang.IllegalArgumentException: Can not construct instance of java.lang.Integer from String value '1000,10': not a valid Integer value\\n at [Source: lambdainternal.util.NativeMemoryAsInputStream@35fc6dc4; line: 1, column: 1] (through reference chain: java.lang.Object[0])",
			    "errorType": "java.lang.IllegalStateException",
			    "stackTrace": [],
			    "cause": {
			      "errorMessage": "Can not construct instance of java.lang.Integer from String value '1000,10': not a valid Integer value\\n at [Source: lambdainternal.util.NativeMemoryAsInputStream@35fc6dc4; line: 1, column: 1] (through reference chain: java.lang.Object[0])",
			      "errorType": "java.lang.IllegalArgumentException",
			      "stackTrace": [
			        "com.fasterxml.jackson.databind.exc.InvalidFormatException.from(InvalidFormatException.java:55)",
			        "com.fasterxml.jackson.databind.DeserializationContext.weirdStringException(DeserializationContext.java:907)"
			      ]
			    }
			  }
			}
			""";

	/**
	 * Example AWS Lambda response payload for an unhandled {@link RuntimeException}, with a single Jackson-related cause that can't be instantiated, and a single
	 * (truncated) stack trace in the cause.
	 * @apiNote This example is modified from <a href="https://docs.aws.amazon.com/lambda/latest/dg/java-exceptions.html">AWS Lambda function errors in Java</a>.
	 * @see software.amazon.awssdk.services.lambda.model.InvokeResponse.InvokeResponse#payload()
	 */
	static final String EXAMPLE_UNHANDLED_RUNTIME_EXCEPTION_JACKSON_CAUSE_RESPONSE_PAYLOAD = """
			{
			  "errorMessage": "An error occurred during JSON parsing",
			  "errorType": "java.lang.RuntimeException",
			  "stackTrace": [],
			  "cause": {
			    "errorMessage": "Can not construct instance of java.lang.Integer from String value '1000,10'",
			    "errorType": "com.fasterxml.jackson.databind.exc.InvalidFormatException",
			    "stackTrace": [
			      "com.fasterxml.jackson.databind.exc.InvalidFormatException.from(InvalidFormatException.java:55)",
			      "com.fasterxml.jackson.databind.DeserializationContext.weirdStringException(DeserializationContext.java:907)"
			    ]
			  }
			}
			""";

	/** @see AwsLambda#unmarshalUnhandledError(InputStream) */
	@Test
	void testUnmarshalUnhandledIllegalArgumentExceptionType() throws IOException {
		final UnhandledError unhandledError = AwsLambda.unmarshalUnhandledError(new ByteArrayInputStream("""
				{
				  "errorType": "java.lang.IllegalArgumentException"
				}
				""".getBytes(UTF_8)));
		assertThat(unhandledError.errorType(), is("java.lang.IllegalArgumentException"));
		assertThat(unhandledError.errorMessage(), is(nullValue()));
		assertThat(unhandledError.stackTrace(), is(empty()));
		assertThat(unhandledError.cause(), is(nullValue()));
	}

	/** @see AwsLambda#unmarshalUnhandledError(InputStream) */
	@Test
	void testUnmarshalUnhandledIllegalArgumentExceptionTypeMessage() throws IOException {
		final UnhandledError unhandledError = AwsLambda.unmarshalUnhandledError(new ByteArrayInputStream("""
				{
				  "errorType": "java.lang.IllegalArgumentException",
				  "errorMessage": "something bad"
				}
				""".getBytes(UTF_8)));
		assertThat(unhandledError.errorType(), is("java.lang.IllegalArgumentException"));
		assertThat(unhandledError.errorMessage(), is("something bad"));
		assertThat(unhandledError.stackTrace(), is(empty()));
		assertThat(unhandledError.cause(), is(nullValue()));
	}

	/** @see AwsLambda#unmarshalUnhandledError(InputStream) */
	@Test
	void testUnmarshalUnhandledIllegalArgumentExceptionTypeMessageEmptyStackTrace() throws IOException {
		final UnhandledError unhandledError = AwsLambda.unmarshalUnhandledError(new ByteArrayInputStream("""
				{
				  "errorType": "java.lang.IllegalArgumentException",
				  "errorMessage": "something bad",
				  "stackTrace": []
				}
				""".getBytes(UTF_8)));
		assertThat(unhandledError.errorType(), is("java.lang.IllegalArgumentException"));
		assertThat(unhandledError.errorMessage(), is("something bad"));
		assertThat(unhandledError.stackTrace(), is(empty()));
		assertThat(unhandledError.cause(), is(nullValue()));
	}

	/**
	 * @see AwsLambda#unmarshalUnhandledError(InputStream)
	 * @ee #EXAMPLE_UNHANDLED_ILLEGAL_ARGUMENT_EXCEPTION_RESPONSE_PAYLOAD
	 */
	@Test
	void testUnmarshalUnhandledIllegalArgumentException() throws IOException {
		assertThat(AwsLambda.unmarshalUnhandledError(new ByteArrayInputStream(EXAMPLE_UNHANDLED_ILLEGAL_ARGUMENT_EXCEPTION_RESPONSE_PAYLOAD.getBytes(UTF_8))),
				is(UnhandledError.fromThrowable(new IllegalArgumentException("test illegal argument exception from user service"),
						"dev.flange.example.cloud.hellouser_faas.service.user.impl.UserServiceImpl.findUserProfileByUsername(UserServiceImpl.java:45)",
						"java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)",
						"java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(Unknown Source)",
						"java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(Unknown Source)", "java.base/java.lang.reflect.Method.invoke(Unknown Source)",
						"dev.flange.cloud.aws.AbstractAwsCloudFunctionServiceHandler.invokeService(AbstractAwsCloudFunctionServiceHandler.java:168)",
						"dev.flange.cloud.aws.AbstractAwsCloudFunctionServiceHandler.handleRequest(AbstractAwsCloudFunctionServiceHandler.java:105)",
						"java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)",
						"java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(Unknown Source)",
						"java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(Unknown Source)",
						"java.base/java.lang.reflect.Method.invoke(Unknown Source)")));
	}

	/**
	 * @see AwsLambda#unmarshalUnhandledError(InputStream)
	 * @see #EXAMPLE_UNHANDLED_IO_EXCEPTION_WITH_ILLEGAL_ARGUMENT_EXCEPTION_CAUSE_RESPONSE_PAYLOAD
	 */
	@Test
	void testUnmarshalUnhandledIOExceptionWithIllegalArgumentExceptionCause() throws IOException {
		assertThat(
				AwsLambda.unmarshalUnhandledError(
						new ByteArrayInputStream(EXAMPLE_UNHANDLED_IO_EXCEPTION_WITH_ILLEGAL_ARGUMENT_EXCEPTION_CAUSE_RESPONSE_PAYLOAD.getBytes(UTF_8))),
				is(UnhandledError.fromThrowable(new IOException("I/O problem"), List.of("com.Example.foo(FooBar.java:789)", "com.Example.foo(FooBar.java:456)"),
						UnhandledError.fromThrowable(new IllegalArgumentException("bad input"), "com.Example.foo(FooBar.java:123)"))));
	}

	/**
	 * @see AwsLambda#unmarshalUnhandledError(InputStream)
	 * @ee #EXAMPLE_UNHANDLED_RUNTIME_EXCEPTION_TWO_CAUSE_LEVELS_RESPONSE_PAYLOAD
	 */
	@Test
	void testUnmarshalUnhandledRuntimeExceptionTwoCauseLevels() throws IOException {
		assertThat(
				AwsLambda.unmarshalUnhandledError(new ByteArrayInputStream(EXAMPLE_UNHANDLED_RUNTIME_EXCEPTION_TWO_CAUSE_LEVELS_RESPONSE_PAYLOAD.getBytes(UTF_8))),
				is(UnhandledError.fromThrowable(new RuntimeException("An error occurred during JSON parsing"), UnhandledError.fromThrowable(new IllegalStateException(
						"java.lang.IllegalArgumentException: Can not construct instance of java.lang.Integer from String value '1000,10': not a valid Integer value\n at [Source: lambdainternal.util.NativeMemoryAsInputStream@35fc6dc4; line: 1, column: 1] (through reference chain: java.lang.Object[0])"),
						UnhandledError.fromThrowable(new IllegalArgumentException(
								"Can not construct instance of java.lang.Integer from String value '1000,10': not a valid Integer value\n at [Source: lambdainternal.util.NativeMemoryAsInputStream@35fc6dc4; line: 1, column: 1] (through reference chain: java.lang.Object[0])"),
								"com.fasterxml.jackson.databind.exc.InvalidFormatException.from(InvalidFormatException.java:55)",
								"com.fasterxml.jackson.databind.DeserializationContext.weirdStringException(DeserializationContext.java:907)")))));
	}

	/** @see AwsLambda.UnhandledError#createThrowable() */
	@Test
	void testUnhandledErrorCreateThrowable() {
		assertEqualUnhandledErrorThrowables("Unchecked exception with no message and no stack trace.",
				new UnhandledError(null, IllegalArgumentException.class.getName(), List.of(), null).createThrowable(), new IllegalArgumentException((String)null),
				false);
		assertEqualUnhandledErrorThrowables("Checked exception with no stack trace.",
				new UnhandledError("I/O problem", IOException.class.getName(), List.of(), null).createThrowable(), new IOException("I/O problem"), false);
		assertEqualUnhandledErrorThrowables("Unknown throwable type gets placeholder.",
				new UnhandledError("Unknown", "com.example.NoSuchThrowable", List.of(), null).createThrowable(),
				new UnavailableMarshalledThrowable("com.example.NoSuchThrowable", "Unknown", null), false);
		assertEqualUnhandledErrorThrowables("Throwable type without appropriate constructor gets placeholder.",
				new UnhandledError("Bad pattern.", PatternSyntaxException.class.getName(), List.of(), null).createThrowable(),
				new UnavailableMarshalledThrowable(PatternSyntaxException.class.getName(), "Bad pattern.", null), false);
		assertEqualUnhandledErrorThrowables("Placeholder exception supports mising message.",
				new UnhandledError(null, PatternSyntaxException.class.getName(), List.of(), null).createThrowable(),
				new UnavailableMarshalledThrowable(PatternSyntaxException.class.getName(), null, null), false);
		assertEqualUnhandledErrorThrowables("Checked exception with cause and no stack trace.",
				new UnhandledError("I/O problem", IOException.class.getName(), List.of(),
						new UnhandledError("Bad input", IllegalArgumentException.class.getName(), List.of(), null)).createThrowable(),
				new IOException("I/O problem", new IllegalArgumentException("Bad input")), false);

	}

	/** @see AwsLambda.UnhandledError#toThrowable() */
	@Test
	void testUnhandledErrorToThrowable() {
		assertEqualUnhandledErrorThrowables("Unchecked exception with no message and no stack trace.",
				new UnhandledError(null, IllegalArgumentException.class.getName(), List.of(), null).toThrowable(),
				clearStackTrace(new IllegalArgumentException((String)null)));
		assertEqualUnhandledErrorThrowables("Checked exception with no stack trace.",
				new UnhandledError("I/O problem", IOException.class.getName(), List.of(), null).toThrowable(), clearStackTrace(new IOException("I/O problem")));
		{
			final List<String> stackTraceStrings = List.of(
					"dev.flange.example.cloud.hellouser_faas.service.user.impl.UserServiceImpl.findUserProfileByUsername(UserServiceImpl.java:45)",
					"java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)",
					"java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(Unknown Source)",
					"java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(Unknown Source)", "java.base/java.lang.reflect.Method.invoke(Unknown Source)",
					"dev.flange.cloud.aws.AbstractAwsCloudFunctionServiceHandler.invokeService(AbstractAwsCloudFunctionServiceHandler.java:168)",
					"dev.flange.cloud.aws.AbstractAwsCloudFunctionServiceHandler.handleRequest(AbstractAwsCloudFunctionServiceHandler.java:105)",
					"java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)",
					"java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(Unknown Source)",
					"java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(Unknown Source)");
			assertEqualUnhandledErrorThrowables("Unchecked exception with stack trace.",
					new UnhandledError("test illegal argument exception from user service", IllegalArgumentException.class.getName(), stackTraceStrings, null)
							.toThrowable(),
					setStackTrace(new IllegalArgumentException("test illegal argument exception from user service"),
							stackTraceStrings.stream().map(StackTrace::parseElement).toArray(StackTraceElement[]::new)));
		}
	}

	/**
	 * Asserts that whether the two throwables are equal in relation to the attributes marshalled by AWS Lambda in an {@link UnhandledError} serialization,
	 * including having the same stack trace.
	 * @apiNote Note that there is an absence of {@link Throwable#equals(Object)} implementations, which would check check local attributes such as
	 *          {@link java.util.regex.PatternSyntaxException.PatternSyntaxException#getIndex()}; moreover such attributes aren't marshalled by AWS Lambda anyway
	 *          for unhandled errors.
	 * @implSpec This implementation compares {@link Throwable#getClass()}, {@link Throwable#getMessage()}, {@link Throwable#getStackTrace()}, and recursively
	 *           {@link Throwable#getCause()}. Notably {@link Throwable#getSuppressed()} is not checked because suppressed exceptions are not marshalled.
	 * @param reason The reason string to include with any error.
	 * @param actualThrowable The actual throwable being compared.
	 * @param expectedThrowable The expected throwable against which to compare the other throwable.
	 * @throws NullPointerException if either throwable is <code>null</code>.
	 */
	static void assertEqualUnhandledErrorThrowables(@Nonnull final String reason, @Nonnull final Throwable actualThrowable,
			@Nonnull final Throwable expectedThrowable) {
		assertEqualUnhandledErrorThrowables(reason, actualThrowable, expectedThrowable, true);
	}

	/**
	 * Asserts that whether the two throwables are equal in relation to the attributes marshalled by AWS Lambda in an {@link UnhandledError} serialization.
	 * @apiNote Note that there is an absence of {@link Throwable#equals(Object)} implementations, which would check check local attributes such as
	 *          {@link java.util.regex.PatternSyntaxException.PatternSyntaxException#getIndex()}; moreover such attributes aren't marshalled by AWS Lambda anyway
	 *          for unhandled errors.
	 * @implSpec This implementation compares {@link Throwable#getClass()}, {@link Throwable#getMessage()}, {@link Throwable#getStackTrace()}, and recursively
	 *           {@link Throwable#getCause()}. Notably {@link Throwable#getSuppressed()} is not checked because suppressed exceptions are not marshalled.
	 * @param reason The reason string to include with any error.
	 * @param actualThrowable The actual throwable being compared.
	 * @param expectedThrowable The expected throwable against which to compare the other throwable.
	 * @param compareStackTrace Whether the stack traces should be compared as well.
	 * @throws NullPointerException if either throwable is <code>null</code>.
	 */
	static void assertEqualUnhandledErrorThrowables(@Nonnull final String reason, @Nonnull final Throwable actualThrowable,
			@Nonnull final Throwable expectedThrowable, final boolean compareStackTrace) {
		assertThat("Throwables are of the same type. (%s)".formatted(reason), actualThrowable.getClass(), is(expectedThrowable.getClass()));
		assertThat("Throwables have the same message. (%s)".formatted(reason), actualThrowable.getMessage(), is(expectedThrowable.getMessage()));
		if(compareStackTrace) {
			assertThat("Throwables have the same stack trace. (%s)".formatted(reason), actualThrowable.getStackTrace(), is(expectedThrowable.getStackTrace()));
		}
		final Throwable actualCause = actualThrowable.getCause();
		final Throwable expectedCause = expectedThrowable.getCause();
		if(expectedCause != null) {
			assertThat("Cause isn't `null`. (%s)".formatted(reason), actualCause, not(nullValue()));
			assertEqualUnhandledErrorThrowables(reason, actualCause, expectedCause, compareStackTrace);
		} else {
			assertThat("Cause is `null`. (%s)".formatted(reason), actualCause, nullValue());
		}
	}

}

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

import static dev.flange.cloud.aws.AbstractAwsCloudFunctionServiceHandler.*;
import static dev.flange.cloud.aws.Marshalling.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

import org.junit.jupiter.api.*;

import com.fasterxml.classmate.GenericType;
import com.globalmentor.util.stream.Streams;

import dev.flange.cloud.aws.MarshallingTest.FooBar;

/**
 * Tests of {@link AbstractAwsCloudFunctionServiceHandler}.
 * @author Garret Wilson
 */
public class AbstractAwsCloudFunctionServiceHandlerTest {

	/** @see AbstractAwsCloudFunctionServiceHandler#unmarshalMethodArgs(List, List) */
	@Test
	void testUnmarshalMethodArgs() {
		assertThat("no arguments", AbstractAwsCloudFunctionServiceHandler.unmarshalMethodArgs(List.of(), List.of()), is(emptyList()));
		assertThat("manual argument list",
				AbstractAwsCloudFunctionServiceHandler.unmarshalMethodArgs(List.of("en", Map.of("foo", Map.of("value", 123), "bar", "foobar"), "en"),
						List.of(String.class, typeTokenToType(new GenericType<FooBar>() {}), Locale.class)),
				is(List.of("en", new FooBar(new FooBar.Foo(123), "foobar"), Locale.forLanguageTag("en"))));
		final Method fooApiBarMethod = declaredMethodsHavingName(FooApi.class, "bar").collect(Streams.toOnly());
		assertThat("method argument list",
				AbstractAwsCloudFunctionServiceHandler.unmarshalMethodArgs(List.of(List.of("en", "fr"), 123, Map.of("foo", Map.of("value", 123), "bar", "foobar")),
						asList(fooApiBarMethod.getGenericParameterTypes())),
				is(List.of(List.of(Locale.ENGLISH, Locale.FRENCH), 123L, new FooBar(new FooBar.Foo(123), "foobar"))));
	}

	private interface FooApi {
		public Set<Instant> bar(List<Locale> locales, long count, FooBar foobar);
	}

	@SuppressWarnings("unused")
	private static class Foo implements FooApi {
		@Override
		public Set<Instant> bar(List<Locale> locales, long count, FooBar foobar) {
			throw new UnsupportedOperationException();
		}
	}

}

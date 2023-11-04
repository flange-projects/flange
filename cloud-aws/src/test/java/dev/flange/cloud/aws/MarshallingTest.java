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

import static dev.flange.cloud.aws.Marshalling.*;
import static java.nio.charset.StandardCharsets.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.classmate.GenericType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * Tests of {@link Marshalling}.
 * @author Garret Wilson
 */
public class MarshallingTest {

	/**
	 * Sanity test to ensure that Jackson parses basic types to a map as expected.
	 * @see ObjectReader#readValue(String)
	 */
	@Test
	void testJacksonReadToMap() throws IOException {
		assertThat(JSON_READER.readValue("{\"foo\": [\"bar\", 123, true]}", Map.class), is(Map.of("foo", List.of("bar", 123, true))));
	}

	/** @see Marshalling#typeTokenToJavaType(TypeFactory, Object) */
	@Test
	void testGenericTypeToJavaType() {
		final GenericType<?> genericType = new GenericType<List<String>>() {};
		final TypeReference<?> typeReference = new TypeReference<List<String>>() {};
		final TypeFactory typeFactory = TypeFactory.defaultInstance();
		final JavaType javaTypeFromTypeReference = typeFactory.constructType(typeReference.getType());
		assertThat(Marshalling.typeTokenToJavaType(typeFactory, genericType), is(javaTypeFromTypeReference));
	}

	/** @see Marshalling#convertValue(Object, java.lang.reflect.Type) */
	@Test
	void testConvertValue() {
		assertThat("`String` to `String`", Marshalling.convertValue("foo", String.class), is("foo"));
		assertThat("`Integer` to `Integer`", Marshalling.convertValue(123, Integer.class), is(123));
		assertThat("`Integer` to `int`", Marshalling.convertValue(123, int.class), is(123));
		assertThat("`Integer` to `Long`", Marshalling.convertValue(123, Long.class), is(123L));
		assertThat("`String` to `Locale`", Marshalling.convertValue("en-US", Locale.class), is(Locale.forLanguageTag("en-US")));
		assertThat("`List<String>` to `List<String>`", Marshalling.convertValue(List.of("en", "fr"), typeTokenToType(new GenericType<List<String>>() {})),
				is(List.of("en", "fr")));
		assertThat("`List<String>` to `List<Locale>`", Marshalling.convertValue(List.of("en", "fr"), typeTokenToType(new GenericType<List<Locale>>() {})),
				is(List.of(Locale.ENGLISH, Locale.FRENCH)));
		assertThat("`List<Integer>` to `List<Integer>`", Marshalling.convertValue(List.of(1, 2, 3), typeTokenToType(new GenericType<List<Integer>>() {})),
				is(List.of(1, 2, 3)));
		assertThat("`List<Integer>` to `List<Long>`", Marshalling.convertValue(List.of(1, 2, 3), typeTokenToType(new GenericType<List<Long>>() {})),
				is(List.of(1L, 2L, 3L)));
		assertThat("`Map<String, Object>` to `Foobar`",
				Marshalling.convertValue(Map.of("foo", Map.of("value", 123), "bar", "foobar"), typeTokenToType(new GenericType<FooBar>() {})),
				is(new FooBar(new FooBar.Foo(123), "foobar")));
	}

	/**
	 * Test marshalling a value. Significantly confirms that {@link Optional} serialization occurs as expected.
	 * @see Marshalling#marshalJson(Object, OutputStream)
	 */
	@Test
	void testMarshalJson() throws IOException {
		assertThat(new String(Marshalling.marshalJson(null, new ByteArrayOutputStream()).toByteArray(), UTF_8), is("null"));
		assertThat(new String(Marshalling.marshalJson("foo", new ByteArrayOutputStream()).toByteArray(), UTF_8), is("\"foo\""));
		assertThat(new String(Marshalling.marshalJson(Optional.empty(), new ByteArrayOutputStream()).toByteArray(), UTF_8), is("null"));
		assertThat(new String(Marshalling.marshalJson(Optional.of("bar"), new ByteArrayOutputStream()).toByteArray(), UTF_8), is("\"bar\""));
	}

	/**
	 * Test unmarshalling a value. Significantly confirms that {@link Optional} deserialization occurs as expected.
	 * @see Marshalling#unmarshalJson(InputStream, GenericType)
	 */
	@Test
	void testUnmarshalJson() throws IOException {
		assertThat("`null` for expected `void` (e.g. `void` method return value)",
				Marshalling.unmarshalJson(new ByteArrayInputStream("null".getBytes(UTF_8)), new GenericType<Void>() {}), is(nullValue()));
		assertThat("`null` for expected non-`void` type (not expected in real life)",
				Marshalling.unmarshalJson(new ByteArrayInputStream("\"bad\"".getBytes(UTF_8)), new GenericType<Void>() {}), is(nullValue()));
		assertThat(Marshalling.unmarshalJson(new ByteArrayInputStream("null".getBytes(UTF_8)), new GenericType<String>() {}), is(nullValue()));
		assertThat(Marshalling.unmarshalJson(new ByteArrayInputStream("\"foo\"".getBytes(UTF_8)), new GenericType<String>() {}), is("foo"));
		assertThat(Marshalling.unmarshalJson(new ByteArrayInputStream("null".getBytes(UTF_8)), new GenericType<Optional<String>>() {}), is(Optional.empty()));
		assertThat(Marshalling.unmarshalJson(new ByteArrayInputStream("\"bar\"".getBytes(UTF_8)), new GenericType<Optional<String>>() {}), is(Optional.of("bar")));
	}

	public record FooBar(Foo foo, String bar) {

		public record Foo(int value) {
		}

	}
}

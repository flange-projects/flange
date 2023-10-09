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

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.classmate.GenericType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * Tests of {@link Marshalling}.
 * @author Garret Wilson
 */
public class MarshallingTest {

	/** @see Marshalling#toJavaType(TypeFactory, GenericType) */
	@Test
	void testGenericTypeToJavaType() {
		@SuppressWarnings("serial")
		final GenericType<?> genericType = new GenericType<List<String>>() {};
		final TypeReference<?> typeReference = new TypeReference<List<String>>() {};
		final TypeFactory typeFactory = TypeFactory.defaultInstance();
		final JavaType javaTypeFromTypeReference = typeFactory.constructType(typeReference.getType());
		assertThat(Marshalling.toJavaType(typeFactory, genericType), is(javaTypeFromTypeReference));
	}

}

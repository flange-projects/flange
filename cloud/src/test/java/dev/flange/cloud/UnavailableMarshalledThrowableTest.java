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

package dev.flange.cloud;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

import dev.flange.cloud.UnavailableMarshalledThrowable.MarshalledThrowableDescription;

/**
 * Tests of {@link UnavailableMarshalledThrowable}.
 * @author Garret Wilson
 */
public class UnavailableMarshalledThrowableTest {

	@Test
	void testMessageConstructor() {
		final IllegalStateException cause = new IllegalStateException("Root cause.");
		final UnavailableMarshalledThrowable unavailableMarshalledThrowable = new UnavailableMarshalledThrowable("(com.example.Foo) The original message.", cause);
		assertThat(unavailableMarshalledThrowable.getMessage(), is("(com.example.Foo) The original message."));
		assertThat(unavailableMarshalledThrowable.getMarshalledThrowableDescription(),
				is(new MarshalledThrowableDescription("com.example.Foo", "The original message.")));
		assertThat(unavailableMarshalledThrowable.getCause(), is(cause));
	}

	/**
	 * @see UnavailableMarshalledThrowable.MarshalledThrowableDescription#fromMessage(CharSequence)
	 */
	@Test
	void testMarshalledThrowableDescriptionFromMessage() {
		assertThat(MarshalledThrowableDescription.fromMessage("()"), is(new MarshalledThrowableDescription("", null)));
		assertThat(MarshalledThrowableDescription.fromMessage("(com.example.Foo)"), is(new MarshalledThrowableDescription("com.example.Foo", null)));
		assertThat(MarshalledThrowableDescription.fromMessage("(com.example.Foo) "), is(new MarshalledThrowableDescription("com.example.Foo", "")));
		assertThat(MarshalledThrowableDescription.fromMessage("(com.example.Foo) x"), is(new MarshalledThrowableDescription("com.example.Foo", "x")));
		assertThat(MarshalledThrowableDescription.fromMessage("(com.example.Foo) The original message."),
				is(new MarshalledThrowableDescription("com.example.Foo", "The original message.")));
		assertThat(MarshalledThrowableDescription.fromMessage("(com.example.Foo) A message which (itself) has parentheses."),
				is(new MarshalledThrowableDescription("com.example.Foo", "A message which (itself) has parentheses.")));
		assertThrows(IllegalArgumentException.class, () -> MarshalledThrowableDescription.fromMessage(""), "Empty string.");
		assertThrows(IllegalArgumentException.class, () -> MarshalledThrowableDescription.fromMessage("("), "Unclosed parentheses.");
		assertThrows(IllegalArgumentException.class, () -> MarshalledThrowableDescription.fromMessage("(com.example.Foo)The message."), "No space before message.");
	}

	/**
	 * @see UnavailableMarshalledThrowable.MarshalledThrowableDescription#toMessage()
	 */
	@Test
	void testMarshalledThrowableDescriptionToMessage() {
		assertThat(new MarshalledThrowableDescription("", null).toMessage(), MarshalledThrowableDescription.fromMessage("()"),
				is(new MarshalledThrowableDescription("", null)));
		assertThat(new MarshalledThrowableDescription("com.example.Foo", null).toMessage(), is("(com.example.Foo)"));
		assertThat(new MarshalledThrowableDescription("com.example.Foo", "").toMessage(), is("(com.example.Foo) "));
		assertThat(new MarshalledThrowableDescription("com.example.Foo", "x").toMessage(), is("(com.example.Foo) x"));
		assertThat(new MarshalledThrowableDescription("com.example.Foo", "The original message.").toMessage(), is("(com.example.Foo) The original message."));
		assertThat(new MarshalledThrowableDescription("com.example.Foo", "A message which (itself) has parentheses.").toMessage(),
				is("(com.example.Foo) A message which (itself) has parentheses."));
	}

}

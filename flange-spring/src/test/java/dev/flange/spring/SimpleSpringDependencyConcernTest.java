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

package dev.flange.spring;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.*;

import dev.flange.DependencyConcern;

/**
 * Tests of {@link SimpleSpringDependencyConcern}.
 * @author Garret Wilson
 */
public class SimpleSpringDependencyConcernTest {

	/** Tests a happy path of registering a type and then looking it up in an initialized container. */
	@Test
	void testRegisterLookup() {
		final DependencyConcern dependencyConcern = new SimpleSpringDependencyConcern();
		dependencyConcern.registerDependency(StringBuilder.class);
		assertThat(dependencyConcern.getDependencyInstanceByType(Appendable.class), instanceOf(StringBuilder.class));
	}

}

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

package dev.flange;

import static com.github.npathai.hamcrestopt.OptionalMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.IOException;

import org.junit.jupiter.api.*;

/**
 * Tests of {@link AbstractDependencyConcern}.
 * @author Garret Wilson
 */
public class AbstractDependencyConcernTest {

	private static final String RESOURCE_DEPENDENCIES_LIST_EMPTY = "flange-dependencies-0.lst";
	private static final String RESOURCE_DEPENDENCIES_LIST_ONE = "flange-dependencies-1.lst";
	private static final String RESOURCE_DEPENDENCIES_LIST_TWO = "flange-dependencies-2.lst";
	private static final String RESOURCE_DEPENDENCIES_LIST_TWO_ROOT = "/flange-dependencies-2.lst";
	private static final String RESOURCE_DEPENDENCIES_LIST_DUPLICATE = "flange-dependencies-duplicate.lst";

	@Test
	void testLoadDefinitionListResource() throws IOException {
		assertThat("Empty dependency list.", AbstractDependencyConcern.loadDefinitionListResource(RESOURCE_DEPENDENCIES_LIST_EMPTY), isPresentAnd(is(empty())));
		assertThat("Dependency list with one entry.", AbstractDependencyConcern.loadDefinitionListResource(RESOURCE_DEPENDENCIES_LIST_ONE),
				isPresentAnd(contains("java.lang.StringBuilder")));
		assertThat("Dependency list with two entries.", AbstractDependencyConcern.loadDefinitionListResource(RESOURCE_DEPENDENCIES_LIST_TWO),
				isPresentAnd(contains("java.lang.StringBuffer", "java.lang.StringBuilder")));
		assertThat("Dependency list with two entries in resources root.", AbstractDependencyConcern.loadDefinitionListResource(RESOURCE_DEPENDENCIES_LIST_TWO_ROOT),
				isPresentAnd(contains("java.lang.StringBuilder", "java.util.Random")));
		Assertions.assertThrows(IOException.class, () -> AbstractDependencyConcern.loadDefinitionListResource(RESOURCE_DEPENDENCIES_LIST_DUPLICATE),
				"Dependency list with duplicate.");
	}

}

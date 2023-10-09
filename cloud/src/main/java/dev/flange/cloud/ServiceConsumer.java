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

import java.lang.annotation.*;

/**
 * Annotation indicating that the annotated implementation class is a consumer of some specified service.
 * @implNote This annotation may eventually be superseded by some module-wide configuration, or the metadata of the itself.
 * @author Garret Wilson
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface ServiceConsumer {

	/**
	 * Indicates the Flange cloud services this class consumes.
	 * @apiNote Typically the interface(s) listed will here will also be present as construction dependency injection parameter(s).
	 * @return The interface(s) representing the service(s) that will be consumed.
	 */
	Class<?>[] value();

}

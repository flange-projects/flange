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
 * Annotation indicating that Flange should deploy this service implementation as a cloud Function-as-a-Service (FaaS).
 * <p>
 * Using best practices a service interface and implementation should be separated and placed in different projects; and this annotation should be applied to
 * the service <em>implementation</em> class. The service interface represents how the service should be accessed via an API, not how the service is implemented
 * or deployed.
 * </p>
 * @author Garret Wilson
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface FaasService {

	/** @return A suggested name for the function, or an empty string if no name is suggested. */
	String value() default "";

}

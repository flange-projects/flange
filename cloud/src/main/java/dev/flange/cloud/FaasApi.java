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
 * Annotation indicating that the annotated interface represents a direct connection to a cloud Function-as-a-Service (FaaS) (as opposed to a RESTful API
 * front-end to a FaaS, for example).
 * @apiNote This annotation should be applied to the service <em>API</em>, indicating how the service should be accessed. The service implementation should also
 *          be annotated with the type of cloud service implementation, such as {@link FaasService}.
 * @author Garret Wilson
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface FaasApi {

	/** @return A suggested name for the function, or an empty string if no name is suggested. */
	String value() default "";

}

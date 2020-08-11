/*
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package watch.dependency

import com.oracle.svm.core.annotate.Substitute
import com.oracle.svm.core.annotate.TargetClass
import okhttp3.internal.platform.Jdk9Platform
import okhttp3.internal.platform.Platform

@Suppress("unused") // Used for Graal native-image compilation.
@TargetClass(Platform.Companion::class)
class TargetPlatform {
	@Substitute
		/**
		 * Replace [Platform] logic that handles classpath variability (bad for Graal)
		 * with a fixed implementation based on known setup.
		 */
	fun findPlatform(): Platform = Jdk9Platform.buildIfSupported()!!
}

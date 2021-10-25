/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package calculator.engine.script;


import calculator.engine.annotation.PublicApi;

@PublicApi
public class ValidateInfo {

    private final boolean isValidScript;

    private final String errorMsg;

    public ValidateInfo(boolean isValidScript) {
        this(isValidScript, "");
    }

    public ValidateInfo(boolean isValidScript, String errorMsg) {
        this.isValidScript = isValidScript;
        this.errorMsg = errorMsg;
    }

    /**
     * Whether the script is valid.
     *
     * @return true if the script is valid
     */
    public boolean isValidScript() {
        return isValidScript;
    }

    /**
     * Returns the detail error message string to explain why the script is invalid.
     *
     * @return error message
     */
    public String getErrorMsg() {
        return errorMsg;
    }
}

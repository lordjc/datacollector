/**
 * Copyright 2016 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.lib.salesforce;

import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.FieldSelectorModel;

public class ForceSDCFieldMapping {

  /**
   * Constructor used for unit testing purposes
   * @param salesforceField
   * @param sdcField
   */
  public ForceSDCFieldMapping(final String salesforceField, final String sdcField) {
    this.salesforceField = salesforceField;
    this.sdcField = sdcField;
  }

  /**
   * Parameter-less constructor required.
   */
  public ForceSDCFieldMapping() {}

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue="",
      label = "Salesforce Field",
      description = "The Salesforce field name.",
      displayPosition = 10
  )
  public String salesforceField;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "",
      label = "SDC Field",
      description = "The field in the record to receive the value.",
      displayPosition = 20
  )
  @FieldSelectorModel(singleValued = true)
  public String sdcField;
}
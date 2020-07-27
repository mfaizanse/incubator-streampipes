/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import {Component, Input, OnInit} from "@angular/core";
import {PipelineElementUnion} from "../../../editor-v2/model/editor.model";
import {Pipeline} from "../../../core-model/gen/streampipes-model";
import {PipelineElementTypeUtils} from "../../../editor-v2/utils/editor.utils";

@Component({
    selector: 'pipeline-elements-row',
    templateUrl: './pipeline-elements-row.component.html',
})
export class PipelineElementsRowComponent implements OnInit {

    elementType: string;

    @Input()
    pipeline: Pipeline;

    _element: PipelineElementUnion;

    constructor() {

    }

    ngOnInit() {
        this.updateType();
    }

    get element() {
        return this._element;
    }

    @Input()
    set element(element: PipelineElementUnion) {
        this._element = element;
        this.updateType();
    }

    updateType() {
        this.elementType = PipelineElementTypeUtils.toCssShortHand(PipelineElementTypeUtils.fromType(this._element));
    }

}

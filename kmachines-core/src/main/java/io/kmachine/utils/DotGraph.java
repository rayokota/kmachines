/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kmachine.utils;

import io.kmachine.model.State;
import io.kmachine.model.StateMachine;
import io.kmachine.model.Transition;

public class DotGraph {

    public static String toDotFormat(StateMachine stateMachine) {
        StringBuilder sb = new StringBuilder(getPrefix());

        for (State state : stateMachine.getStates()) {
            sb.append(formatState(state));
        }

        for (Transition transition : stateMachine.getTransitions()) {
            if (transition.getTo() == null
                || transition.getToType() == Transition.ToType.Function) {
                // skip internal transitions
                continue;
            }
            sb.append(formatTransition(transition));
        }

        sb.append("}");
        return sb.toString();
    }

    private static String getPrefix() {
        return String.format("digraph {%n"
            + "node [style=filled]%n");
    }

    private static String formatState(State state) {
        return String.format("\"%s\" [label=\"%s\"];%n", state.getName(), state.getName());
    }

    private static String formatTransition(Transition transition) {
        String label = transition.getType() != null ? transition.getType() : "";
        return String.format("\"%s\" -> \"%s\" [style=\"solid\", label=\"%s\"];%n",
            transition.getFrom(), transition.getTo(), label);
    }
}

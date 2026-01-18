/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.assistant.agent.data.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Single option item for parameter selection.
 * <p>
 * Represents a selectable option with a display label, actual value,
 * and optional description. Used in dropdown lists, radio buttons,
 * and other selection UI components.
 * <p>
 * Example usage:
 * <pre>
 * OptionItem option1 = new OptionItem("Engineering", 1);
 * OptionItem option2 = new OptionItem("Sales", 2, "Sales Department");
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class OptionItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The display label shown to the user.
     */
    private String label;

    /**
     * The actual value to be used when this option is selected.
     */
    private Object value;

    /**
     * Optional description providing additional information about this option.
     */
    private String description;

    /**
     * Default constructor.
     */
    public OptionItem() {
    }

    /**
     * Constructs an option item with label and value.
     *
     * @param label the display label
     * @param value the actual value
     */
    public OptionItem(String label, Object value) {
        this.label = label;
        this.value = value;
    }

    /**
     * Constructs an option item with label, value, and description.
     *
     * @param label the display label
     * @param value the actual value
     * @param description the optional description
     */
    public OptionItem(String label, Object value, String description) {
        this.label = label;
        this.value = value;
        this.description = description;
    }

    /**
     * Gets the display label.
     *
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the display label.
     *
     * @param label the label to set
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Gets the actual value.
     *
     * @return the value
     */
    public Object getValue() {
        return value;
    }

    /**
     * Sets the actual value.
     *
     * @param value the value to set
     */
    public void setValue(Object value) {
        this.value = value;
    }

    /**
     * Gets the description.
     *
     * @return the description, or null if not set
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OptionItem that = (OptionItem) o;
        return Objects.equals(label, that.label) &&
                Objects.equals(value, that.value) &&
                Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, value, description);
    }

    @Override
    public String toString() {
        return label + " (" + value + ")";
    }
}

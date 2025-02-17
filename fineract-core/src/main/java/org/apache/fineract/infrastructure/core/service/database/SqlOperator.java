/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.core.service.database;

import static java.lang.String.format;

import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.fineract.infrastructure.core.exception.PlatformServiceUnavailableException;

@AllArgsConstructor
@Getter
public enum SqlOperator {

    EQ("="), //
    NEQ("<>"), //
    GTE(">="), //
    LTE("<="), //
    GT(">"), //
    LT("<"), //
    LIKE("LIKE") { //

        @Override
        public String formatImpl(@NotNull DatabaseSpecificSQLGenerator sqlGenerator, JdbcJavaType columnType, String definition,
                String... values) {
            return format("%s %s %s", definition, getSymbol(), sqlGenerator.formatValue(columnType, "%" + values[0] + "%"));
        }

        @Override
        public String formatPlaceholderImpl(String definition, String placeholder) {
            return format("%s %s CONCAT('%%', %s, '%%')", definition, getSymbol(), placeholder);
        }
    },
    NLIKE("NOT LIKE") { //

        @Override
        public String formatImpl(@NotNull DatabaseSpecificSQLGenerator sqlGenerator, JdbcJavaType columnType, String definition,
                String... values) {
            return format("%s %s %s", definition, getSymbol(), sqlGenerator.formatValue(columnType, "%" + values[0] + "%"));
        }

        @Override
        public String formatPlaceholderImpl(String definition, String placeholder) {
            return format("%s %s CONCAT('%%', %s, '%%')", definition, getSymbol(), placeholder);
        }
    },
    BTW("BETWEEN", 2) { //

        @Override
        public String formatImpl(@NotNull DatabaseSpecificSQLGenerator sqlGenerator, JdbcJavaType columnType, String definition,
                String... values) {
            return format("%s %s %s AND %s", definition, getSymbol(), sqlGenerator.formatValue(columnType, values[0]),
                    sqlGenerator.formatValue(columnType, values[1]));
        }

        @Override
        public String formatPlaceholderImpl(String definition, String placeholder) {
            return format("%s %s %s AND %s", definition, getSymbol(), placeholder, placeholder);
        }
    },
    NBTW("NOT BETWEEN", 2) { //

        @Override
        public String formatImpl(@NotNull DatabaseSpecificSQLGenerator sqlGenerator, JdbcJavaType columnType, String definition,
                String... values) {
            return format("%s %s %s AND %s", definition, getSymbol(), sqlGenerator.formatValue(columnType, values[0]),
                    sqlGenerator.formatValue(columnType, values[1]));
        }

        @Override
        public String formatPlaceholderImpl(String definition, String placeholder) {
            return format("%s %s %s AND %s", definition, getSymbol(), placeholder, placeholder);
        }
    },
    IN("IN", -1) { //

        @Override
        public String formatImpl(@NotNull DatabaseSpecificSQLGenerator sqlGenerator, JdbcJavaType columnType, String definition,
                String... values) {
            return format("%s %s (%s)", definition, getSymbol(),
                    Arrays.stream(values).map(e -> sqlGenerator.formatValue(columnType, e)).collect(Collectors.joining(", ")));
        }

        @Override
        public boolean isPlaceholderSupported() {
            return false;
        }
    },
    NIN("NOT IN", -1) { //

        @Override
        public String formatImpl(@NotNull DatabaseSpecificSQLGenerator sqlGenerator, JdbcJavaType columnType, String definition,
                String... values) {
            return format("%s %s (%s)", definition, getSymbol(),
                    Arrays.stream(values).map(e -> sqlGenerator.formatValue(columnType, e)).collect(Collectors.joining(", ")));
        }

        @Override
        public boolean isPlaceholderSupported() {
            return false;
        }
    },
    NULL("IS NULL", 0), //
    NNULL("IS NOT NULL", 0), //
    ;

    private final String symbol;
    private final int paramCount;

    SqlOperator(String symbol) {
        this(symbol, 1);
    }

    public boolean isDefault() {
        return this == getDefault();
    }

    public static SqlOperator getDefault() {
        return EQ;
    }

    public boolean isListType() {
        return paramCount < 0;
    }

    public String formatSql(@NotNull DatabaseSpecificSQLGenerator sqlGenerator, JdbcJavaType columnType, String definition, String alias,
            List<String> values) {
        return formatSql(sqlGenerator, columnType, definition, alias, values == null ? null : values.toArray(String[]::new));
    }

    public String formatSql(@NotNull DatabaseSpecificSQLGenerator sqlGenerator, JdbcJavaType columnType, String definition, String alias,
            String... values) {
        validateValues(values);
        return formatImpl(sqlGenerator, columnType, sqlGenerator.alias(sqlGenerator.escape(definition), alias), values);
    }

    protected String formatImpl(@NotNull DatabaseSpecificSQLGenerator sqlGenerator, JdbcJavaType columnType, String definition,
            String... values) {
        return paramCount == 0 ? format("%s %s", definition, symbol)
                : format("%s %s %s", definition, symbol, sqlGenerator.formatValue(columnType, values[0]));
    }

    public boolean isPlaceholderSupported() {
        return true;
    }

    public String formatPlaceholder(@NotNull DatabaseSpecificSQLGenerator sqlGenerator, String definition, String alias) {
        return formatPlaceholder(sqlGenerator, definition, alias, "?");
    }

    public String formatPlaceholder(@NotNull DatabaseSpecificSQLGenerator sqlGenerator, String definition, String alias,
            String placeholder) {
        return formatPlaceholderImpl(sqlGenerator.alias(sqlGenerator.escape(definition), alias), placeholder);
    }

    protected String formatPlaceholderImpl(String definition, String placeholder) {
        if (!isPlaceholderSupported()) {
            throw new UnsupportedOperationException("Placeholder is not supported for this operator");
        }
        return paramCount == 0 ? format("%s %s", definition, symbol) : format("%s %s %s", definition, symbol, placeholder);
    }

    public void validateValues(String... values) {
        if (values == null ? paramCount != 0 : (paramCount < 0 ? values.length < -paramCount : values.length != paramCount)) {
            throw new PlatformServiceUnavailableException("error.msg.database.operator.invalid",
                    "Number of parameters " + Arrays.toString(values) + " must be " + Math.abs(paramCount) + " on " + this);
        }
    }

    public void validateValues(List<?> values) {
        int size = values == null ? 0 : values.size();
        if (paramCount < 0 ? size < -paramCount : size != paramCount) {
            throw new PlatformServiceUnavailableException("error.msg.database.operator.invalid",
                    "Number of parameters " + size + " must be " + Math.abs(paramCount) + " on " + this);
        }
    }

    @NotNull
    public static SqlOperator forName(String name) {
        return name == null ? getDefault() : SqlOperator.valueOf(name.toUpperCase());
    }
}

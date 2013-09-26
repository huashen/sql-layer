/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.foundationdb.sql.pg;

import com.foundationdb.sql.server.ServerCallExplainer;
import com.foundationdb.sql.server.ServerCallInvocation;

import com.foundationdb.qp.operator.QueryBindings;
import com.foundationdb.qp.operator.SparseArrayQueryBindings;
import com.foundationdb.qp.loadableplan.LoadableDirectObjectPlan;
import com.foundationdb.qp.loadableplan.LoadableOperator;
import com.foundationdb.qp.loadableplan.LoadablePlan;
import com.foundationdb.server.explain.Attributes;
import com.foundationdb.server.explain.CompoundExplainer;
import com.foundationdb.server.explain.ExplainContext;
import com.foundationdb.server.explain.Explainable;
import com.foundationdb.server.explain.Label;
import com.foundationdb.server.explain.PrimitiveExplainer;
import com.foundationdb.server.types.AkType;
import com.foundationdb.server.types3.TInstance;
import com.foundationdb.sql.optimizer.TypesTranslation;
import com.foundationdb.sql.types.DataTypeDescriptor;

import java.util.ArrayList;
import java.util.List;

public class PostgresLoadablePlan
{
    public static PostgresStatement statement(PostgresServerSession server, 
                                              ServerCallInvocation invocation) {
        LoadablePlan<?> loadablePlan = 
            server.getRoutineLoader().loadLoadablePlan(server.getSession(),
                                                       invocation.getRoutineName());
        List<String> columnNames = loadablePlan.columnNames();
        List<PostgresType> columnTypes = columnTypes(loadablePlan);
        if (loadablePlan instanceof LoadableOperator)
            return new PostgresLoadableOperator((LoadableOperator)loadablePlan, 
                                                invocation,
                                                columnNames, columnTypes,
                                                null);
        if (loadablePlan instanceof LoadableDirectObjectPlan)
            return new PostgresLoadableDirectObjectPlan((LoadableDirectObjectPlan)loadablePlan, 
                                                        invocation,
                                                        columnNames, columnTypes,
                                                        null);
        return null;
    }

    public static QueryBindings setParameters(QueryBindings bindings, ServerCallInvocation invocation) {
        if (!invocation.parametersInOrder()) {
            if (invocation.hasParameters()) {
                QueryBindings calleeBindings = new SparseArrayQueryBindings();
                invocation.copyParameters(bindings, calleeBindings);
                bindings = calleeBindings;
            }
            else {
                invocation.copyParameters(null, bindings);
            }
        }
        return bindings;
    }

    public static List<PostgresType> columnTypes(LoadablePlan<?> plan)
    {
        List<PostgresType> columnTypes = new ArrayList<>();
        for (int jdbcType : plan.jdbcTypes()) {
            DataTypeDescriptor sqlType = DataTypeDescriptor.getBuiltInDataTypeDescriptor(jdbcType);
            AkType akType = TypesTranslation.sqlTypeToAkType(sqlType);
            TInstance tInstance = TypesTranslation.toTInstance(sqlType);
            columnTypes.add(PostgresType.fromDerby(sqlType, akType, tInstance));
        }
        return columnTypes;
    }

    public static Explainable explainable(PostgresServerSession server, 
                                          final ServerCallInvocation invocation) {
        final LoadablePlan<?> loadablePlan = 
            server.getRoutineLoader().loadLoadablePlan(server.getSession(),
                                                       invocation.getRoutineName());
        return new Explainable() {
                @Override
                public CompoundExplainer getExplainer(ExplainContext context) {
                    Attributes atts = new Attributes();
                    atts.put(Label.PROCEDURE_IMPLEMENTATION, 
                             PrimitiveExplainer.getInstance(loadablePlan.getClass().getName()));
                    return new ServerCallExplainer(invocation, atts, context);
                }
            };
    }

    // All static methods.
    private PostgresLoadablePlan() {
    }
}
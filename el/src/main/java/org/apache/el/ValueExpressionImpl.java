/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.el;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import com.guet.flexbox.el.ELContext;
import com.guet.flexbox.el.ELException;
import com.guet.flexbox.el.FunctionMapper;
import com.guet.flexbox.el.ExpressionFactory;
import com.guet.flexbox.el.PropertyNotFoundException;
import com.guet.flexbox.el.PropertyNotWritableException;
import com.guet.flexbox.el.ELResolver;
import com.guet.flexbox.el.Expression;
import com.guet.flexbox.el.ValueExpression;
import com.guet.flexbox.el.ValueReference;
import com.guet.flexbox.el.VariableMapper;

import org.apache.el.lang.EvaluationContext;
import org.apache.el.lang.ExpressionBuilder;
import org.apache.el.parser.AstLiteralExpression;
import org.apache.el.parser.Node;
import org.apache.el.util.ReflectionUtil;


/**
 * An <code>Expression</code> that can get or set a value.
 *
 * <p>
 * In previous incarnations of this API, expressions could only be read.
 * <code>ValueExpression</code> objects can now be used both to retrieve a
 * value and to set a value. Expressions that can have a value set on them are
 * referred to as l-value expressions. Those that cannot are referred to as
 * r-value expressions. Not all r-value expressions can be used as l-value
 * expressions (e.g. <code>"${1+1}"</code> or
 * <code>"${firstName} ${lastName}"</code>). See the EL Specification for
 * details. Expressions that cannot be used as l-values must always return
 * <code>true</code> from <code>isReadOnly()</code>.
 * </p>
 *
 * <p>
 * The {@link ExpressionFactory#createValueExpression} method
 * can be used to parse an expression string and return a concrete instance
 * of <code>ValueExpression</code> that encapsulates the parsed expression.
 * The {@link FunctionMapper} is used at parse time, not evaluation time,
 * so one is not needed to evaluate an expression using this class.
 * However, the {@link ELContext} is needed at evaluation time.</p>
 *
 * <p>The {@link #getValue}, {@link #setValue}, {@link #isReadOnly} and
 * {@link #getType} methods will evaluate the expression each time they are
 * called. The {@link ELResolver} in the <code>ELContext</code> is used
 * to resolve the top-level variables and to determine the behavior of the
 * <code>.</code> and <code>[]</code> operators. For any of the four methods,
 * the {@link ELResolver#getValue} method is used to resolve all
 * properties up to but excluding the last one. This provides the
 * <code>base</code> object. At the last resolution, the
 * <code>ValueExpression</code> will call the corresponding
 * {@link ELResolver#getValue}, {@link ELResolver#setValue},
 * {@link ELResolver#isReadOnly} or {@link ELResolver#getType}
 * method, depending on which was called on the <code>ValueExpression</code>.
 * </p>
 *
 * <p>See the notes about comparison, serialization and immutability in
 * the {@link Expression} javadocs.
 *
 * @see ELResolver
 * @see Expression
 * @see ExpressionFactory
 * @see ValueExpression
 *
 * @author Jacob Hookom [jacob@hookom.net]
 */
public final class ValueExpressionImpl extends ValueExpression implements
        Externalizable {

    private Class<?> expectedType;

    private String expr;

    private FunctionMapper fnMapper;

    private VariableMapper varMapper;

    private transient Node node;

    public ValueExpressionImpl() {
        super();
    }

    public ValueExpressionImpl(String expr, Node node, FunctionMapper fnMapper,
            VariableMapper varMapper, Class<?> expectedType) {
        this.expr = expr;
        this.node = node;
        this.fnMapper = fnMapper;
        this.varMapper = varMapper;
        this.expectedType = expectedType;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ValueExpressionImpl)) {
            return false;
        }
        if (obj.hashCode() != this.hashCode()) {
            return false;
        }

        return this.getNode().equals(((ValueExpressionImpl) obj).getNode());
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.el.ValueExpression#getExpectedType()
     */
    @Override
    public Class<?> getExpectedType() {
        return this.expectedType;
    }

    /**
     * Returns the type the result of the expression will be coerced to after
     * evaluation.
     *
     * @return the <code>expectedType</code> passed to the
     *         <code>ExpressionFactory.createValueExpression</code> method
     *         that created this <code>ValueExpression</code>.
     *
     * @see Expression#getExpressionString()
     */
    @Override
    public String getExpressionString() {
        return this.expr;
    }

    private Node getNode() throws ELException {
        if (this.node == null) {
            this.node = ExpressionBuilder.createNode(this.expr);
        }
        return this.node;
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.el.ValueExpression#getType(javax.el.ELContext)
     */
    @Override
    public Class<?> getType(ELContext context) throws PropertyNotFoundException,
            ELException {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        context.notifyBeforeEvaluation(getExpressionString());
        Class<?> result = this.getNode().getType(ctx);
        context.notifyAfterEvaluation(getExpressionString());
        return result;
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.el.ValueExpression#getValue(javax.el.ELContext)
     */
    @Override
    public Object getValue(ELContext context) throws PropertyNotFoundException,
            ELException {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        context.notifyBeforeEvaluation(getExpressionString());
        Object value = this.getNode().getValue(ctx);
        if (this.expectedType != null) {
            value = context.convertToType(value, this.expectedType);
        }
        context.notifyAfterEvaluation(getExpressionString());
        return value;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return this.getNode().hashCode();
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.el.ValueExpression#isLiteralText()
     */
    @Override
    public boolean isLiteralText() {
        try {
            return this.getNode() instanceof AstLiteralExpression;
        } catch (ELException ele) {
            return false;
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.el.ValueExpression#isReadOnly(javax.el.ELContext)
     */
    @Override
    public boolean isReadOnly(ELContext context)
            throws PropertyNotFoundException, ELException {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        context.notifyBeforeEvaluation(getExpressionString());
        boolean result = this.getNode().isReadOnly(ctx);
        context.notifyAfterEvaluation(getExpressionString());
        return result;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        this.expr = in.readUTF();
        String type = in.readUTF();
        if (!"".equals(type)) {
            this.expectedType = ReflectionUtil.forName(type);
        }
        this.fnMapper = (FunctionMapper) in.readObject();
        this.varMapper = (VariableMapper) in.readObject();
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.el.ValueExpression#setValue(javax.el.ELContext,
     *      java.lang.Object)
     */
    @Override
    public void setValue(ELContext context, Object value)
            throws PropertyNotFoundException, PropertyNotWritableException,
            ELException {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        context.notifyBeforeEvaluation(getExpressionString());
        this.getNode().setValue(ctx, value);
        context.notifyAfterEvaluation(getExpressionString());
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(this.expr);
        out.writeUTF((this.expectedType != null) ? this.expectedType.getName()
                : "");
        out.writeObject(this.fnMapper);
        out.writeObject(this.varMapper);
    }

    @Override
    public String toString() {
        return "ValueExpression["+this.expr+"]";
    }

    /**
     * @since EL 2.2
     */
    @Override
    public ValueReference getValueReference(ELContext context) {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        context.notifyBeforeEvaluation(getExpressionString());
        ValueReference result = this.getNode().getValueReference(ctx);
        context.notifyAfterEvaluation(getExpressionString());
        return result;
    }
}

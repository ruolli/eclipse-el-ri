/*
 * Copyright (c) 2012, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package javax.el;

import java.beans.FeatureDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Iterator;

/**
 * <p>
 * An {@link ELResolver} for resolving static fields, enum constants and static methods. Also handles constructor calls
 * as a special case.
 * </p>
 * <p>
 * The resolver handles base objects of the type {@link ELClass}, which is usually generated by an EL implementation.
 * </p>
 *
 * @see ELClass
 * @since EL 3.0
 */
public class StaticFieldELResolver extends ELResolver {

    /**
     * <p>
     * Returns the value of a static field.
     * </p>
     * <p>
     * If the base object is an instance of <code>ELClass</code> and the property is String, the
     * <code>propertyResolved</code> property of the <code>ELContext</code> object must be set to <code>true</code> by this
     * resolver, before returning. If this property is not <code>true</code> after this method is called, the caller should
     * ignore the return value.
     * </p>
     *
     * If the property is a public static field of class specified in <code>ELClass</code>, return the value of the static
     * field. An Enum constant is a public static field of an Enum object, and is a special case of this.
     *
     * @param context The context of this evaluation.
     * @param base An <code>ELClass</code>.
     * @param property A static field name.
     * @return If the <code>propertyResolved</code> property of <code>ELContext</code> was set to <code>true</code>, then
     * the static field value.
     * @throws NullPointerException if context is <code>null</code>.
     * @throws PropertyNotFoundException if the specified class does not exist, or if the field is not a public static filed
     * of the class, or if the field is inaccessible.
     */
    @Override
    public Object getValue(ELContext context, Object base, Object property) {

        if (context == null) {
            throw new NullPointerException();
        }

        if (base instanceof ELClass && property instanceof String) {
            Class<?> klass = ((ELClass) base).getKlass();
            String fieldName = (String) property;
            try {
                context.setPropertyResolved(base, property);
                Field field = klass.getField(fieldName);
                int mod = field.getModifiers();
                if (Modifier.isPublic(mod) && Modifier.isStatic(mod)) {
                    return field.get(null);
                }
            } catch (NoSuchFieldException ex) {
            } catch (IllegalAccessException ex) {
            }
            throw new PropertyNotFoundException(ELUtil.getExceptionMessageString(context, "staticFieldReadError", new Object[] { klass.getName(), fieldName }));
        }
        return null;
    }

    /**
     * <p>
     * Attempts to write to a static field.
     * </p>
     * <p>
     * If the base object is an instance of <code>ELClass</code>and the property is String, a
     * <code>PropertyNotWritableException</code> will always be thrown, because writing to a static field is not allowed.
     *
     * @param context The context of this evaluation.
     * @param base An <code>ELClass</code>
     * @param property The name of the field
     * @param value The value to set the field of the class to.
     * @throws NullPointerException if context is <code>null</code>
     * @throws PropertyNotWritableException if base object instance of <code>ELClass</code>and <code>property</code>
     * instance of String
     */
    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        if (context == null) {
            throw new NullPointerException();
        }
        if (base instanceof ELClass && property instanceof String) {
            Class<?> klass = ((ELClass) base).getKlass();
            String fieldName = (String) property;
            throw new PropertyNotWritableException(
                    ELUtil.getExceptionMessageString(context, "staticFieldWriteError", new Object[] { klass.getName(), fieldName }));
        }
    }

    /**
     * Invokes a public static method or the constructor for a class.
     *
     * <p>
     * If the base object is an instance of <code>ELClass</code> and the method is a String, the
     * <code>propertyResolved</code> property of the <code>ELContext</code> object must be set to <code>true</code> by the
     * resolver, before returning. If this property is not <code>true</code> after this method is called, the caller should
     * ignore the return value.
     *
     * <p>
     * Invoke the public static method specified by <code>method</code>.
     *
     * <p>
     * The process involved in the method selection is the same as that used in {@link BeanELResolver}.
     *
     * <p>
     * As a special case, if the name of the method is "&lt;init&gt;", the constructor for the class will be invoked.
     *
     * @param base An <code>ELClass</code>
     * @param method When coerced to a <code>String</code>, the simple name of the method.
     * @param paramTypes An array of Class objects identifying the method's formal parameter types, in declared order. Use
     * an empty array if the method has no parameters. Can be <code>null</code>, in which case the method's formal parameter
     * types are assumed to be unknown.
     * @param params The parameters to pass to the method, or <code>null</code> if no parameters.
     * @return The result of the method invocation (<code>null</code> if the method has a <code>void</code> return type).
     * @throws MethodNotFoundException if no suitable method can be found.
     * @throws ELException if an exception was thrown while performing (base, method) resolution. The thrown exception must
     * be included as the cause property of this exception, if available. If the exception thrown is an
     * <code>InvocationTargetException</code>, extract its <code>cause</code> and pass it to the <code>ELException</code>
     * constructor.
     */
    @Override
    public Object invoke(ELContext context, Object base, Object method, Class<?>[] paramTypes, Object[] params) {

        if (context == null) {
            throw new NullPointerException();
        }

        if (!(base instanceof ELClass && method instanceof String)) {
            return null;
        }

        Class<?> klass = ((ELClass) base).getKlass();
        String name = (String) method;

        Object ret;
        if ("<init>".equals(name)) {
            Constructor<?> constructor = ELUtil.findConstructor(klass, paramTypes, params);
            ret = ELUtil.invokeConstructor(context, constructor, params);
        } else {
            Method meth = ELUtil.findMethod(klass, name, paramTypes, params, true);
            ret = ELUtil.invokeMethod(context, meth, null, params);
        }
        context.setPropertyResolved(base, method);
        return ret;
    }

    /**
     * Returns the type of a static field.
     *
     * <p>
     * If the base object is an instance of <code>ELClass</code>and the property is a String, the
     * <code>propertyResolved</code> property of the <code>ELContext</code> object must be set to <code>true</code> by the
     * resolver, before returning. If this property is not <code>true</code> after this method is called, the caller can
     * safely assume no value has been set.
     *
     * <p>
     * If the property string is a public static field of class specified in ELClass, return the type of the static field.
     *
     * @param context The context of this evaluation.
     * @param base An <code>ELClass</code>.
     * @param property The name of the field.
     * @return If the <code>propertyResolved</code> property of <code>ELContext</code> was set to <code>true</code>, then
     * the type of the type of the field.
     * @throws NullPointerException if context is <code>null</code>.
     * @throws PropertyNotFoundException if field is not a public static filed of the class, or if the field is
     * inaccessible.
     */
    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {

        if (context == null) {
            throw new NullPointerException();
        }

        if (base instanceof ELClass && property instanceof String) {
            Class<?> klass = ((ELClass) base).getKlass();
            String fieldName = (String) property;
            try {
                context.setPropertyResolved(true);
                Field field = klass.getField(fieldName);
                int mod = field.getModifiers();
                if (Modifier.isPublic(mod) && Modifier.isStatic(mod)) {
                    return field.getType();
                }
            } catch (NoSuchFieldException ex) {
            }
            throw new PropertyNotFoundException(ELUtil.getExceptionMessageString(context, "staticFieldReadError", new Object[] { klass.getName(), fieldName }));
        }
        return null;
    }

    /**
     * <p>
     * Inquires whether the static field is writable.
     * </p>
     * <p>
     * If the base object is an instance of <code>ELClass</code>and the property is a String, the
     * <code>propertyResolved</code> property of the <code>ELContext</code> object must be set to <code>true</code> by the
     * resolver, before returning. If this property is not <code>true</code> after this method is called, the caller can
     * safely assume no value has been set.
     * </p>
     *
     * <p>
     * Always returns a <code>true</code> because writing to a static field is not allowed.
     * </p>
     *
     * @param context The context of this evaluation.
     * @param base An <code>ELClass</code>.
     * @param property The name of the bean.
     * @return <code>true</code>
     * @throws NullPointerException if context is <code>null</code>.
     */
    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        if (context == null) {
            throw new NullPointerException();
        }

        if (base instanceof ELClass && property instanceof String) {
            Class<?> klass = ((ELClass) base).getKlass();
            context.setPropertyResolved(true);
        }
        return true;
    }

    /**
     * Returns the properties that can be resolved. Always returns <code>null</code>, since there is no reason to iterate
     * through a list of one element: field name.
     *
     * @param context The context of this evaluation.
     * @param base An <code>ELClass</code>.
     * @return <code>null</code>.
     */
    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return null;
    }

    /**
     * Returns the type of the property. Always returns <code>String.class</code>, since a field name is a String.
     *
     * @param context The context of this evaluation.
     * @param base An <code>ELClass</code>.
     * @return <code>String.class</code>.
     */
    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return String.class;
    }
}

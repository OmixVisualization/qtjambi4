/****************************************************************************
**
** Copyright (C) 1992-2009 Nokia. All rights reserved.
**
** This file is part of Qt Jambi.
**
** ** $BEGIN_LICENSE$
** Commercial Usage
** Licensees holding valid Qt Commercial licenses may use this file in
** accordance with the Qt Commercial License Agreement provided with the
** Software or, alternatively, in accordance with the terms contained in
** a written agreement between you and Nokia.
** 
** GNU Lesser General Public License Usage
** Alternatively, this file may be used under the terms of the GNU Lesser
** General Public License version 2.1 as published by the Free Software
** Foundation and appearing in the file LICENSE.LGPL included in the
** packaging of this file.  Please review the following information to
** ensure the GNU Lesser General Public License version 2.1 requirements
** will be met: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html.
** 
** In addition, as a special exception, Nokia gives you certain
** additional rights. These rights are described in the Nokia Qt LGPL
** Exception version 1.0, included in the file LGPL_EXCEPTION.txt in this
** package.
** 
** GNU General Public License Usage
** Alternatively, this file may be used under the terms of the GNU
** General Public License version 3.0 as published by the Free Software
** Foundation and appearing in the file LICENSE.GPL included in the
** packaging of this file.  Please review the following information to
** ensure the GNU General Public License version 3.0 requirements will be
** met: http://www.gnu.org/copyleft/gpl.html.
** 
** If you are unsure which license is appropriate for your use, please
** contact the sales department at qt-sales@nokia.com.
** $END_LICENSE$

**
** This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
** WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
**
****************************************************************************/

package com.trolltech.qt.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;

import com.trolltech.qt.QFlags;
import com.trolltech.qt.QSignalEmitter;
import com.trolltech.qt.QtBlockedEnum;
import com.trolltech.qt.QtBlockedSlot;
import com.trolltech.qt.QtEnumerator;
import com.trolltech.qt.QtPropertyDesignable;
import com.trolltech.qt.QtPropertyNotify;
import com.trolltech.qt.QtPropertyReader;
import com.trolltech.qt.QtPropertyResetter;
import com.trolltech.qt.QtPropertyScriptable;
import com.trolltech.qt.QtPropertyUser;
import com.trolltech.qt.QtPropertyWriter;
import com.trolltech.qt.QtScriptable;
import com.trolltech.qt.Utilities;
import com.trolltech.qt.core.QObject;
import com.trolltech.qt.core.Qt;
import com.trolltech.qt.gui.QStyle;


/**
 * Methods to help construct the fake meta object.
 */
public class MetaObjectTools {

    private static class Container {
        private enum AnnotationType {
            Reader,
            Writer,
            Resetter,
            Notify
        }

        private Member member;
        private String name = null;
        private boolean enabled;
        private AnnotationType type;


        private Container(String name, Member member, boolean enabled, AnnotationType type) {
            this.name = name;
            this.member = member;
            this.enabled = enabled;
            this.type = type;
        }

        private Container(QtPropertyReader reader, Method method) {
            this(reader.name(), method, reader.enabled(), AnnotationType.Reader);
        }

        private Container(QtPropertyWriter writer, Method method) {
            this(writer.name(), method, writer.enabled(), AnnotationType.Writer);
        }

        private Container(QtPropertyResetter resetter, Method method) {
            this(resetter.name(), method, resetter.enabled(), AnnotationType.Resetter);
        }
        
        private Container(QtPropertyNotify notify, Field method) {
            this(notify.name(), method, notify.enabled(), AnnotationType.Notify);
        }

        private static String removeAndLowercaseFirst(String name, int count) {
            return Character.toLowerCase(name.charAt(count)) + name.substring(count + 1);
        }

        private String getNameFromMethod(Member method) {
            if (type == AnnotationType.Resetter) {
                return "";
            } else if (type == AnnotationType.Notify) {
                return "";
            } else if (type == AnnotationType.Reader) {
                String name = method.getName();
                int len = name.length();
                if (name.startsWith("get") && len > 3)
                    name = removeAndLowercaseFirst(name, 3);
                else if (isBoolean(((Method)method).getReturnType()) && name.startsWith("is") && len > 2)
                    name = removeAndLowercaseFirst(name, 2);
                else if (isBoolean(((Method)method).getReturnType()) && name.startsWith("has") && len > 3)
                    name = removeAndLowercaseFirst(name, 3);

                return name;
            } else { // starts with "set"
                String name = method.getName();
                if (!name.startsWith("set") || name.length() <= 3) {
                    throw new IllegalArgumentException("The correct pattern for setter accessor names is setXxx where Xxx is the property name with upper case initial.");
                }

                name = removeAndLowercaseFirst(name, 3);
                return name;
            }
        }

        private String name() {
            if (name == null || name.length() == 0)
                name = getNameFromMethod(member);

            return name;
        }

        private boolean enabled() {
            return enabled;
        }

        private static Container readerAnnotation(Method method) {
            QtPropertyReader reader = method.getAnnotation(QtPropertyReader.class);
            return reader == null ? null : new Container(reader, method);
        }

        private static Container writerAnnotation(Method method) {
            QtPropertyWriter writer = method.getAnnotation(QtPropertyWriter.class);
            return writer == null ? null : new Container(writer, method);
        }

        private static Container resetterAnnotation(Method method) {
            QtPropertyResetter resetter = method.getAnnotation(QtPropertyResetter.class);
            return resetter == null ? null : new Container(resetter, method);
        }
        
        private static Container notifyAnnotation(Field field) {
            QtPropertyNotify notify = field.getAnnotation(QtPropertyNotify.class);
            return notify == null ? null : new Container(notify, field);
        }

    }

    private static class MetaData {
        public int metaData[];
        public byte stringData[];

		public Field signalsArray[];
        public Method slotsArray[];
        public Constructor<?> constructorsArray[];

        public Method propertyReadersArray[];
        public Method propertyWritersArray[];
        public Method propertyResettersArray[];
        public Field propertyNotifiesArray[];
        public Method propertyDesignablesArray[];
        public Method propertyScriptablesArray[];
        public Class<?> extraDataArray[];

        public String originalSignatures[];
    }

    private final static int MethodAccessPrivate                    = 0x0;
    private final static int MethodAccessProtected                  = 0x1;
    private final static int MethodAccessPublic                     = 0x2;
    private final static int MethodAccessMask                       = 0x3;

    private final static int MethodSignal                           = 0x4;
    private final static int MethodSlot                             = 0x8;
    private final static int MethodConstructor                      = 0xc;
    private final static int MethodTypeMask                         = 0xc;

    private final static int MethodCompatibility                    = 0x10;
    private final static int MethodCloned                           = 0x20;
    private final static int MethodScriptable                       = 0x40;
    private final static int MethodRevisioned 						= 0x80;

    private final static int PropertyReadable                       = 0x00000001;
    private final static int PropertyWritable                       = 0x00000002;
    private final static int PropertyResettable                     = 0x00000004;
    private final static int PropertyEnumOrFlag                     = 0x00000008;
    private final static int PropertyDesignable                     = 0x00001000;
    private final static int PropertyResolveDesignable              = 0x00002000;
    private final static int PropertyScriptable                     = 0x00004000;
    private final static int PropertyResolveScriptable              = 0x00008000;
    private final static int PropertyStored                         = 0x00010000;
    private final static int PropertyUser                           = 0x00100000;
    private final static int PropertyResolveUser  					= 0x00200000;
    private final static int PropertyNotify 						= 0x00400000;

    private static Method notBogus(Method method, String propertyName, Class<?> paramType) {
        if (method == null)
            return null;

        Container reader = Container.readerAnnotation(method);
        if (reader != null
            && (!reader.name().equals(propertyName)
                || !reader.enabled()
                || !method.getReturnType().isAssignableFrom(paramType))) {
            return null;
        } else {
            return method;
        }
    }

    private static int queryEnums(Class<?> clazz, Hashtable<String, Class<?>> enums) {
        int enumConstantCount = 0;

        Class<?> declaredClasses[] = clazz.getDeclaredClasses();
        for (Class<?> declaredClass : declaredClasses)
            enumConstantCount += putEnumTypeInHash(declaredClass, enums);

        return enumConstantCount;
    }

    private static Class<?> getEnumForQFlags(Class<?> flagsType) {
        Type t = flagsType.getGenericSuperclass();
        if (t instanceof ParameterizedType) {
            Type typeArguments[] = ((ParameterizedType)t).getActualTypeArguments();
            return ((Class<?>) typeArguments[0]);
        }

        return null;
    }

    private static int putEnumTypeInHash(Class<?> type, Hashtable<String, Class<?>> enums) {
        Class<?> flagsType = QFlags.class.isAssignableFrom(type) ? type : null;
        Class<?> enumType = type.isEnum() ? type : null;
        if (enumType == null && flagsType != null) {
            enumType = getEnumForQFlags(flagsType);
        }

        if (enumType == null)
            return 0;

        // Since Qt supports enums that are not part of the meta object
        // we need to check whether the enum can actually be used in
        // a property.
        Class<?> enclosingClass = enumType.getEnclosingClass();
        if (enclosingClass == null){
        	return -1;
        }
        if (((!QObject.class.isAssignableFrom(enclosingClass) && !Qt.class.equals(enclosingClass))
               || enumType.isAnnotationPresent(QtBlockedEnum.class))) {
            return -1;
        }

        int enumConstantCount = 0;
        if (!enums.contains(enumType.getName())) {
            enums.put(enumType.getName(), enumType);

            enumConstantCount = enumType.getEnumConstants().length;
        }

        if (flagsType != null && !enums.contains(flagsType.getName()))
            enums.put(flagsType.getName(), flagsType);

        return enumConstantCount;
    }

    private static Object isDesignable(Method declaredMethod, Class<?> clazz) {
        QtPropertyDesignable designable = declaredMethod.getAnnotation(QtPropertyDesignable.class);

        if (designable != null) {
            String value = designable.value();

            if (value.equals("true")) {
                return Boolean.TRUE;
            } else if (value.equals("false")) {
                return Boolean.FALSE;
            } else try {
                Method m = clazz.getMethod(value, (Class<?>[]) null);
                if (isBoolean(m.getReturnType()))
                    return m;
                else
                    throw new RuntimeException("Wrong return type of designable method '" + m.getName() + "'");
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        return Boolean.TRUE;
    }
    
    private static Object isScriptable(Method declaredMethod, Class<?> clazz) {
        QtPropertyScriptable scriptable = declaredMethod.getAnnotation(QtPropertyScriptable.class);

        if (scriptable != null) {
            String value = scriptable.value();

            if (value.equals("true")) {
                return Boolean.TRUE;
            } else if (value.equals("false")) {
                return Boolean.FALSE;
            } else try {
                Method m = clazz.getMethod(value, (Class<?>[]) null);
                if (isBoolean(m.getReturnType()))
                    return m;
                else
                    throw new RuntimeException("Wrong return type of scriptable method '" + m.getName() + "'");
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        return Boolean.TRUE;
    }

    private static boolean isValidSetter(Method declaredMethod) {
        return (declaredMethod.getParameterTypes().length == 1
                && declaredMethod.getReturnType() == Void.TYPE
                && !internalTypeNameOfParameters(declaredMethod, 1).equals(""));
    }

    private static Method getMethod(Class<?> clazz, String name, Class<?> args[]) {
        try {
            return clazz.getMethod(name, args);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static String capitalizeFirst(String str) {
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private static boolean isBoolean(Class<?> type) {
        return (type == Boolean.class || type == Boolean.TYPE);
    }

    private static Boolean isUser(Method m) {
        return (m.getAnnotation(QtPropertyUser.class) != null);
    }


    private static boolean isValidGetter(Method method) {
        return (method.getParameterTypes().length == 0
                && method.getReturnType() != Void.TYPE);
    }


    public static String bunchOfClassNamesInARow(Class<?> classes[]) {
        return bunchOfClassNamesInARow(classes, null);
    }

    public static String bunchOfClassNamesInARow(Class<?> classes[], int arrayDimensions[]) {
        String classNames = "";

        for (int i=0; i<classes.length; ++i) {
            Class<?> clazz = classes[i];

            String className = clazz.getName();
            if (arrayDimensions != null) {
                for (int j=0; j<arrayDimensions[i]; ++j)
                    className = "java.lang.Object";
            }
            if(!clazz.isPrimitive()){
	            try{
					MetaObjectTools.class.getClassLoader().loadClass(className);
				}catch(ClassNotFoundException e){
					if(QObject.class.isAssignableFrom(clazz)){
						className = QObject.class.getName();
					}else{
						className = Object.class.getName();
					}
				}
            }
			classNames += className;
            if (i<classes.length-1)
                classNames += ",";
        }

        return classNames;
    }

    private static String methodParameters(Method m) {
        return bunchOfClassNamesInARow(m.getParameterTypes());
    }

    /**
     * Returns the signature of the method m excluding the modifiers and the
     * return type.
     */
    private static String methodSignature(Method m, boolean includeReturnType) {
        return (includeReturnType ? m.getReturnType().getName() + " " : "")
               + m.getName() + "(" + methodParameters(m) + ")";
    }

    public static String methodSignature(Method m) {
        return methodSignature(m, false);
    }
    
    private static String constructorSignature(Constructor<?> c) {
    	String name = c.getName();
    	int idx = name.lastIndexOf('.');
    	if(idx>0){
    		name = name.substring(idx+1, name.length());
    	}
        return name + "(" + constructorParameters(c) + ")";
    }
    
    private static String constructorParameters(Constructor<?> c) {
        return bunchOfClassNamesInARow(c.getParameterTypes());
    }


    private static int addString(int metaData[],
                                 Hashtable<String, Integer> strings,
                                 List<String> stringsInOrder,
                                 String string, int offset, int metaDataOffset) {

                if (strings.containsKey(string)) {
                    metaData[metaDataOffset] = strings.get(string);
                    return 0;
                }

                metaData[metaDataOffset] = offset;
                strings.put(string, offset);
                stringsInOrder.add(string);
                return string.length() + 1;
    }

    public native static void emitNativeSignal(QObject object, String signalSignature, String signalCppSignature, Object args[]);

    public static String cppSignalSignature(QSignalEmitterInternal signalEmitter, String signalName) {
        QSignalEmitter.AbstractSignal signal = (QSignalEmitter.AbstractSignal) QtJambiInternal.lookupSignal(signalEmitter, signalName);
        if (signal != null)
            return cppSignalSignature(signal);
        else
            return "";
    }

    public static String cppSignalSignature(QSignalEmitter.AbstractSignalInternal signal) {
        String signalParameters = QtJambiInternal.signalParameters(signal);
        String params = MetaObjectTools.internalTypeNameOfSignal(signal.resolveSignal(), signalParameters, 1);
        if (signalParameters.length() > 0 && params.length() == 0)
            return "";
        else
            return signal.name() + "(" + params + ")";
    }

    private static String signalParameters(QtJambiInternal.ResolvedSignal resolvedSignal) {
        return MetaObjectTools.bunchOfClassNamesInARow(resolvedSignal.types, resolvedSignal.arrayDimensions);
    }

    private static String signalParameters(Field field, Class<?> declaringClass) {
        QtJambiInternal.ResolvedSignal resolvedSignal = QtJambiInternal.resolveSignal(field, declaringClass);
        return signalParameters(resolvedSignal);
    }


    private native static String internalTypeName(String s, int varContext);
    
    /**
     * This method is used from inside the native qtjambi method
     * <tt>QtJambiTypeManager::resolveQFlags(const QString &amp;, const QString &amp;) const</tt>
     * @param cls a QFlags class
     * @return the type argument
     */
    private static Class<?> resolveQFlagsActualTypeArgument(Class<? extends QFlags<?>> cls){
		Type type = cls;
		while(true){
    		if(type  instanceof ParameterizedType){
    			ParameterizedType ptype = (ParameterizedType)type;
    			if(ptype.getRawType()==QFlags.class){
    				Type[] actualTypeArguments = ptype.getActualTypeArguments();
    				if(actualTypeArguments.length==1 && actualTypeArguments[0] instanceof Class<?>){
    					return (Class<?>)actualTypeArguments[0];
    				}else{
    					return QtEnumerator.class;
    				}
    			}else{
    				if(ptype.getRawType() instanceof Class<?>){
    					type = ((Class<?>)ptype.getRawType()).getGenericSuperclass();
    				}else{
    					return QtEnumerator.class; 
    				}
    			}
    		}else if(type instanceof Class){
    			if(type==QFlags.class){
    	    		return QtEnumerator.class;
    			}else{
    				type = ((Class<?>)type).getGenericSuperclass();
    			}
    		}
		}
//    	return null;
    }

    public static String internalTypeNameOfSignal(Class<?>[] signals, String s, int varContext) {
        try {
            return internalTypeName(s, varContext);
        } catch (Throwable t) {
            return "";
        }
    }
    
    public static String internalTypeNameOfParameters(Method declaredMethod, int varContext) {
        try {
        	String s = methodParameters(declaredMethod);
            return internalTypeName(s, varContext);
        } catch (Throwable t) {
            return "";
        }
    }
    
    public static String internalTypeNameOfParameters(Constructor<?> declaredConstructor, int varContext) {
        try {
        	String s = constructorParameters(declaredConstructor);
            return internalTypeName(s, varContext);
        } catch (Throwable t) {
            return "";
        }
    }
    
    public static String internalTypeNameOfMethodSignature(Method slot, int varContext) {
        try {
        	String javaMethodSignature = methodSignature(slot);
            return internalTypeName(javaMethodSignature, varContext);
        } catch (Throwable t) {
            return "";
        }
    }
    
    public static String internalTypeNameOfMethodSignature(Constructor<?> constructor, int varContext) {
        try {
        	String javaMethodSignature = constructorSignature(constructor);
            return internalTypeName(javaMethodSignature, varContext);
        } catch (Throwable t) {
            return "";
        }
    }
    
    public static String internalTypeNameOfClass(Class<? extends Object> cls, int varContext) {
        try {
            String returnType = cls.getName();
            if(!cls.isPrimitive()) {
                try {
                    MetaObjectTools.class.getClassLoader().loadClass(returnType);
                } catch(Exception e) {
                    if(QObject.class.isAssignableFrom(cls)) {
                        returnType = QObject.class.getName();
                    } else {
                        returnType = Object.class.getName();
                    }
                }
            }
            return internalTypeName(returnType, varContext);
        } catch (Throwable t) {
            return "";
        }
    }

    private static MetaData buildMetaData(Class<? extends QObject> clazz) {
        MetaData metaData = new MetaData();

        List<Constructor<?>> constructors = new ArrayList<Constructor<?>>();
        List<Method> slots = new ArrayList<Method>();

        Hashtable<String, Method> propertyReaders = new Hashtable<String, Method>();
        Hashtable<String, Method> propertyWriters = new Hashtable<String, Method>();
        Hashtable<String, Object> propertyDesignables = new Hashtable<String, Object>();
        Hashtable<String, Object> propertyScriptables = new Hashtable<String, Object>();
        Hashtable<String, Method> propertyResetters = new Hashtable<String, Method>();
        Hashtable<String, Field> propertyNotifies = new Hashtable<String, Field>();
        Hashtable<String, Boolean> propertyUser = new Hashtable<String, Boolean>();

        // First we get all enums actually declared in the class
        Hashtable<String, Class<?>> enums = new Hashtable<String, Class<?>>();
        int enumConstantCount = queryEnums(clazz, enums);
        int enumCount = enums.size(); // Get the size before we add external enums
        
        for(Constructor<?> constructor : clazz.getDeclaredConstructors()){
        	constructors.add(constructor);
        }

        Method declaredMethods[] = clazz.getDeclaredMethods();
        for (Method declaredMethod : declaredMethods) {

            if ((!declaredMethod.isAnnotationPresent(QtBlockedSlot.class) || 
            		(declaredMethod.isAnnotationPresent(QtScriptable.class) 
            				&& declaredMethod.getAnnotation(QtScriptable.class).value()))
                    && ((declaredMethod.getModifiers() & Modifier.STATIC) != Modifier.STATIC)) {

                // If we can't convert the type, we don't list it
                String methodParameters = methodParameters(declaredMethod);
                String returnType = declaredMethod.getReturnType().getName();
                if ((methodParameters.equals("") || !internalTypeNameOfParameters(declaredMethod, 1).equals(""))
                    &&(returnType.equals("") || returnType.equals("void") || !internalTypeNameOfClass(declaredMethod.getReturnType(), 0).equals(""))) {
                    slots.add(declaredMethod);
                }
            }

            // Rules for readers:
            // 1. Zero arguments
            // 2. Return something other than void
            // 3. We can convert the type
            Container reader = Container.readerAnnotation(declaredMethod);
            {

                if (reader != null
                    && reader.enabled()
                    && isValidGetter(declaredMethod)
                    && !internalTypeNameOfClass(declaredMethod.getReturnType(), 0).equals("")) {

                    // If the return type of the property reader is not registered, then
                    // we need to register the owner class in the meta object (in which case
                    // it has to be a QObject)
                    Class<?> returnType = declaredMethod.getReturnType();

                    int count = putEnumTypeInHash(returnType, enums);
                    if (count < 0) {
                        System.err.println("Problem in property '" + reader.name() + "' in '" + clazz.getName()
                                           + "': Only enum types 1. declared inside QObject subclasses or the "
                                           + "Qt interface and 2. declared without the QtBlockedEnum annotation "
                                           + "are supported for properties");
                        continue;
                    }

                    propertyReaders.put(reader.name(), declaredMethod);
                    propertyDesignables.put(reader.name(), isDesignable(declaredMethod, clazz));
                    propertyScriptables.put(reader.name(), isScriptable(declaredMethod, clazz));
                    propertyUser.put(reader.name(), isUser(declaredMethod));
                }
            }

            // Rules for writers:
            // 1. Takes exactly one argument
            // 2. Return void
            // 3. We can convert the type
            Container writer = Container.writerAnnotation(declaredMethod);
            {

                if (writer != null
                    && writer.enabled()
                    && isValidSetter(declaredMethod)) {
                    propertyWriters.put(writer.name(), declaredMethod);
                }
            }
            
            // Rules for notifys:
            // 1. No arguments
            // 2. Return void
            Container resetter = Container.resetterAnnotation(declaredMethod);
            {

                if (resetter != null
                    && declaredMethod.getParameterTypes().length == 0
                    && declaredMethod.getReturnType() == Void.TYPE) {
                    propertyResetters.put(resetter.name(), declaredMethod);
                }
            }

            // Check naming convention by looking for setXxx patterns, but only if it hasn't already been
            // annotated as a writer
            String declaredMethodName = declaredMethod.getName();
            if (writer == null
                && reader == null // reader can't be a writer, cause the signature doesn't match, just an optimization
                && declaredMethodName.startsWith("set")
                && declaredMethodName.length() > 3
                && Character.isUpperCase(declaredMethodName.charAt(3))
                && isValidSetter(declaredMethod)) {

                Class<?> paramType = declaredMethod.getParameterTypes()[0];
                String propertyName = Character.toLowerCase(declaredMethodName.charAt(3))
                                    + declaredMethodName.substring(4);

                if (!propertyReaders.containsKey(propertyName)) {
                    // We need a reader as well, and the reader must not be annotated as disabled
                    // The reader can be called 'xxx', 'getXxx', 'isXxx' or 'hasXxx'
                    // (just booleans for the last two)
                    Method readerMethod = notBogus(getMethod(clazz, propertyName, null), propertyName, paramType);
                    if (readerMethod == null)
                        readerMethod = notBogus(getMethod(clazz, "get" + capitalizeFirst(propertyName), null), propertyName, paramType);
                    if (readerMethod == null && isBoolean(paramType))
                        readerMethod = notBogus(getMethod(clazz, "is" + capitalizeFirst(propertyName), null), propertyName, paramType);
                    if (readerMethod == null && isBoolean(paramType))
                        readerMethod = notBogus(getMethod(clazz, "has" + capitalizeFirst(propertyName), null), propertyName, paramType);

                    if (readerMethod != null) { // yay
                        reader = Container.readerAnnotation(readerMethod);
                        if (reader == null) {
                            propertyReaders.put(propertyName, readerMethod);
                            propertyWriters.put(propertyName, declaredMethod);

                            propertyDesignables.put(propertyName, isDesignable(readerMethod, clazz));
                            propertyScriptables.put(propertyName, isScriptable(readerMethod, clazz));
                            propertyUser.put(propertyName, isUser(readerMethod));
                        }
                    }
                }
            }
        }

        Field declaredFields[] = clazz.getDeclaredFields();
        List<Field> signalFields = new ArrayList<Field>();
        List<QtJambiInternal.ResolvedSignal> resolvedSignals = new ArrayList<QtJambiInternal.ResolvedSignal>();
        for (Field declaredField : declaredFields) {
            if (QtJambiInternal.isSignal(declaredField.getType())) {
                // If we can't convert all the types we don't list the signal
                QtJambiInternal.ResolvedSignal resolvedSignal = QtJambiInternal.resolveSignal(declaredField, declaredField.getDeclaringClass());
                String signalParameters = signalParameters(resolvedSignal);
                if (signalParameters.length() == 0 || internalTypeNameOfSignal(resolvedSignal.types, signalParameters, 1).length() != 0) {
                    signalFields.add(declaredField);
                    resolvedSignals.add(resolvedSignal);
                }

                // Rules for resetters:
                // 1. Zero or one argument
                {
                    Container notify = Container.notifyAnnotation(declaredField);

                    if (notify != null
                        && resolvedSignal.types.length <= 1) {
                        propertyNotifies.put(notify.name(), declaredField);
                    }
                }
            }
        }
        metaData.signalsArray = signalFields.toArray(new Field[0]);

        {
            int functionCount = slots.size() + signalFields.size();
            int propertyCount = propertyReaders.keySet().size();
            int propertyNotifierCount = propertyNotifies.keySet().size();  // FIXME (see QTabWidget)
            int constructorCount = constructors.size();  // FIXME (see QObject)
            int metaObjectFlags = 0;  // FIXME DynamicMetaObject

            // Until 4.7.x QtJambi used revision=1 however due to a change in the way
            //  4.7.x works some features of QtJambi stopped working.
            // revision 1         = MO_HEADER_LEN=10
            // revision 2 (4.5.x) = MO_HEADER_LEN=12 (added: constructorCount, constructorData)
            // revision 3         = MO_HEADER_LEN=13 (added: flags)
            // revision 4 (4.6.x) = MO_HEADER_LEN=14 (added: signalCount)
            // revision 5 (4.7.x) = MO_HEADER_LEN=14 (normalization)
            // revision 6 (4.8.x) = MO_HEADER_LEN=14 (added support for qt_static_metacall)
            // revision 7 (5.0.x) = MO_HEADER_LEN=14 (Qt5 to break backwards compatibility)
            // The format is compatible to share the same encoding code
            //  then we can change the revision to suit the Qt
            /// implementation we are working with.

            final int MO_HEADER_LEN = 14;  // header size
            final int CLASSINFO_LEN = 2;  // class info size
            int len = MO_HEADER_LEN + CLASSINFO_LEN;
            len += functionCount * 5;
            len += propertyCount * 3;
            len += propertyNotifierCount;
            len += enumCount * 4;
            len += enumConstantCount * 2;
            len += constructorCount * 5;
            metaData.metaData = new int[len + 1]; // add EOD <NUL>

            int intDataOffset = 0;  // the offsets used by this descriptor are based on ints (32bit values)

            // Add static header
            metaData.metaData[0] = resolveMetaDataRevision();  // Revision
            // metaData[1] = 0 // class name  (ints default to offset 0 into strings)
            intDataOffset += MO_HEADER_LEN;

            metaData.metaData[2] = 1;  // class info count
            metaData.metaData[3] = intDataOffset; // class info offset
            intDataOffset += CLASSINFO_LEN;

            // Functions always start right after this header at offset MO_HEADER_LEN + CLASSINFO_LEN
            metaData.metaData[4] = functionCount;
            metaData.metaData[5] = functionCount == 0 ? 0 : intDataOffset;
            intDataOffset += functionCount * 5;  // Each function takes 5 ints

            metaData.metaData[6] = propertyCount;
            metaData.metaData[7] = propertyCount == 0 ? 0 : intDataOffset;
            intDataOffset += (propertyCount * 3) + propertyNotifierCount;  // Each property takes 3 ints and each propertNotifier 1 int

            // Enums
            metaData.metaData[8] = enumCount;
            metaData.metaData[9] = enumCount == 0 ? 0 : intDataOffset;
            intDataOffset += (enumCount * 4) + (enumConstantCount * 2);  // Each enum takes 4 ints and each enumConst takes 2 ints
            // revision 1 ends here

            metaData.metaData[10] = constructorCount;
            metaData.metaData[11] = constructorCount == 0 ? 0 : intDataOffset;
            intDataOffset += constructorCount * 5;
            // revision 2 ends here
            metaData.metaData[12] = metaObjectFlags; // flags
            // revision 3 ends here
            metaData.metaData[13] = signalFields.size(); // signalCount
            // revision 4, 5 and 6 ends here

            int offset = 0;
            int metaDataOffset = MO_HEADER_LEN; // Header is currently 14 ints long
            Hashtable<String, Integer> strings = new Hashtable<String, Integer>();
            List<String> stringsInOrder = new ArrayList<String>();
            // Class name
            {
                String className = clazz.getName().replace(".", "::");
                stringsInOrder.add(className);
                strings.put(className, offset); offset += className.length() + 1;
            }

            // Class info
            {
                offset += addString(metaData.metaData, strings, stringsInOrder, "__qt__binding_shell_language", offset, metaDataOffset++);
                offset += addString(metaData.metaData, strings, stringsInOrder, "Qt Jambi", offset, metaDataOffset++);
            }

            metaData.originalSignatures = new String[functionCount + constructors.size()];

            // Signals (### make sure enum types are covered)
            for (int i=0; i<signalFields.size(); ++i) {
                Field signalField = signalFields.get(i);
                QtJambiInternal.ResolvedSignal resolvedSignal = resolvedSignals.get(i);

                String javaSignalParameters = signalParameters(resolvedSignal);
                metaData.originalSignatures[i] = resolvedSignal.name
                    + (javaSignalParameters.length() > 0 ? '<' + javaSignalParameters + '>' : "");

                String signalParameters = internalTypeNameOfSignal(resolvedSignal.types, javaSignalParameters, 1);

                // Signal name
                offset += addString(metaData.metaData, strings, stringsInOrder, resolvedSignal.name + "(" + signalParameters + ")", offset, metaDataOffset++);

                // Signal parameters
                offset += addString(metaData.metaData, strings, stringsInOrder, signalParameters, offset, metaDataOffset++);

                // Signal type (signals are always void in Qt Jambi)
                offset += addString(metaData.metaData, strings, stringsInOrder, "", offset, metaDataOffset++);

                // Signal tag (Currently not supported by the moc either)
                offset += addString(metaData.metaData, strings, stringsInOrder, "", offset, metaDataOffset++);

                // Signal flags
                int flags = MethodSignal;
                int modifiers = signalField.getModifiers();
                if ((modifiers & Modifier.PRIVATE) == Modifier.PRIVATE)
                    flags |= MethodAccessPrivate;
                else if ((modifiers & Modifier.PROTECTED) == Modifier.PROTECTED)
                    flags |= MethodAccessProtected;
                else if ((modifiers & Modifier.PUBLIC) == Modifier.PUBLIC)
                    flags |= MethodAccessPublic;
                if(signalField.isAnnotationPresent(QtScriptable.class) && signalField.getAnnotation(QtScriptable.class).value()){
                	flags |= MethodScriptable;
                }
                
                metaData.metaData[metaDataOffset++] = flags;
            }

            // Slots (### make sure enum types are covered, ### also test QFlags)
            for (int i=0; i<slots.size(); ++i) {
                Method slot = slots.get(i);

                String javaMethodSignature = methodSignature(slot);
                metaData.originalSignatures[signalFields.size() + i] = javaMethodSignature;

                // Slot signature
                String internalTypeNameOfMethodSignature = internalTypeNameOfMethodSignature(slot, 1);
                String internalTypeNameOfParameters = internalTypeNameOfParameters(slot, 1);
                
                // this is a workaround for an internal Qt bug in QStyle.
                // QStyle dynamically calls method 'standardIconImplementation(StandardPixmap,const QStyleOption*,const QWidget*)'
                // instead of using the full type name
                // Tested with Qt 4.8.4 Maybe version check is required.
                if(QStyle.class.isAssignableFrom(clazz) && internalTypeNameOfMethodSignature.equals("standardIconImplementation(QStyle::StandardPixmap,QStyleOption,QWidget*)")){
                	internalTypeNameOfMethodSignature = "standardIconImplementation(StandardPixmap,const QStyleOption*,const QWidget*)";
                	internalTypeNameOfParameters = "StandardPixmap,const QStyleOption*,const QWidget*";
                }
            
                offset += addString(metaData.metaData, strings, stringsInOrder, internalTypeNameOfMethodSignature, offset, metaDataOffset++);

                // Slot parameters
                offset += addString(metaData.metaData, strings, stringsInOrder, internalTypeNameOfParameters, offset, metaDataOffset++);

                // Slot type
                String returnType = slot.getReturnType().getName();
                if (returnType.equals("void")) returnType = "";
                offset += addString(metaData.metaData, strings, stringsInOrder, internalTypeNameOfClass(slot.getReturnType(), 0), offset, metaDataOffset++);

                // Slot tag (Currently not supported by the moc either)
                offset += addString(metaData.metaData, strings, stringsInOrder, "", offset, metaDataOffset++);

                // Slot flags
                int flags = slot.isAnnotationPresent(QtBlockedSlot.class) ? 0 : MethodSlot;
                int modifiers = slot.getModifiers();
                if ((modifiers & Modifier.PRIVATE) == Modifier.PRIVATE)
                    flags |= MethodAccessPrivate;
                else if ((modifiers & Modifier.PROTECTED) == Modifier.PROTECTED)
                    flags |= MethodAccessProtected;
                else if ((modifiers & Modifier.PUBLIC) == Modifier.PUBLIC)
                    flags |= MethodAccessPublic;
                if(slot.isAnnotationPresent(QtScriptable.class) && slot.getAnnotation(QtScriptable.class).value()){
                	flags |= MethodScriptable;
                }

                metaData.metaData[metaDataOffset++] = flags;
            }
            metaData.slotsArray = slots.toArray(new Method[0]);
            
            String propertyNames[] = propertyReaders.keySet().toArray(new String[0]);
            metaData.propertyReadersArray = new Method[propertyNames.length];
            metaData.propertyResettersArray = new Method[propertyNames.length];
            metaData.propertyNotifiesArray = new Field[propertyNames.length];
            metaData.propertyWritersArray = new Method[propertyNames.length];
            metaData.propertyDesignablesArray = new Method[propertyNames.length];
            metaData.propertyScriptablesArray = new Method[propertyNames.length];
            for (int i=0; i<propertyNames.length; ++i) {
                Method reader = propertyReaders.get(propertyNames[i]);
                Method writer = propertyWriters.get(propertyNames[i]);
                Method resetter = propertyResetters.get(propertyNames[i]);
                Field notify = propertyNotifies.get(propertyNames[i]);
                Object designableVariant = propertyDesignables.get(propertyNames[i]);
                Object scriptableVariant = propertyScriptables.get(propertyNames[i]);
                boolean isUser = propertyUser.get(propertyNames[i]);

                if (writer != null && !reader.getReturnType().isAssignableFrom(writer.getParameterTypes()[0])) {
                    System.err.println("QtJambiInternal.buildMetaData: Writer for property "
                            + propertyNames[i] + " takes a type which is incompatible with reader's return type.");
                    writer = null;
                }

                // Name
                offset += addString(metaData.metaData, strings, stringsInOrder, propertyNames[i], offset, metaDataOffset++);

                // Type (need to special case flags and enums)
                Class<?> t = reader.getReturnType();
                boolean isEnumOrFlags = Enum.class.isAssignableFrom(t) || QFlags.class.isAssignableFrom(t);

                String typeName = null;
                if (isEnumOrFlags && t.getDeclaringClass() != null && QObject.class.isAssignableFrom(t.getDeclaringClass())) {
                    // To avoid using JObjectWrapper for enums and flags (which is required in certain cases.)
                    typeName = t.getDeclaringClass().getName().replace(".", "::") + "::" + t.getSimpleName();
                } else {
                    typeName = internalTypeNameOfClass(t, 0);
                }
                offset += addString(metaData.metaData, strings, stringsInOrder, typeName, offset, metaDataOffset++);

                int designableFlags = 0;
                if (designableVariant instanceof Boolean) {
                    if ((Boolean) designableVariant) designableFlags = PropertyDesignable;
                } else if (designableVariant instanceof Method) {
                    designableFlags = PropertyResolveDesignable;
                    metaData.propertyDesignablesArray[i] = (Method) designableVariant;
                }
                
                int scriptableFlags = PropertyScriptable;
                if (scriptableVariant instanceof Boolean) {
                    if (!(Boolean) scriptableVariant) scriptableFlags = 0;
                } else if (scriptableVariant instanceof Method) {
                	scriptableFlags = PropertyResolveScriptable;
                    metaData.propertyScriptablesArray[i] = (Method) scriptableVariant;
                }

                // Flags
                metaData.metaData[metaDataOffset++] = PropertyReadable | PropertyStored | designableFlags | scriptableFlags
                    | (writer != null ? PropertyWritable : 0)
                    | (resetter != null ? PropertyResettable : 0)
                    | (notify != null ? PropertyNotify : 0)
                    | (isEnumOrFlags ? PropertyEnumOrFlag : 0)
                    | (isUser ? PropertyUser : 0);

                metaData.propertyReadersArray[i] = reader;
                metaData.propertyWritersArray[i] = writer;
                metaData.propertyResettersArray[i] = resetter;
                metaData.propertyNotifiesArray[i] = notify;
            }
            
            for (int i=0; i<propertyNames.length; ++i) {
                Field notify = propertyNotifies.get(propertyNames[i]);
                if(notify!=null){
                	metaData.metaData[metaDataOffset++] = signalFields.indexOf(notify);
                }
            }

            // Each property notifier takes 1 int // FIXME

            // Enum types
            int enumConstantOffset = metaDataOffset + enumCount * 4;

            Hashtable<String, Class<?>> enclosingClasses = new Hashtable<String, Class<?>>();
            Collection<Class<?>> classes = enums.values();
            for (Class<?> cls : classes) {
                Class<?> enclosingClass = cls.getEnclosingClass();
                if(enclosingClass!=null){ // might use standalone enum classes
	                if (enclosingClass.equals(clazz)) {
	                    // Name
	                    offset += addString(metaData.metaData, strings, stringsInOrder, cls.getSimpleName(), offset, metaDataOffset++);
	
	                    // Flags (1 == flags, 0 == no flags)
	                    metaData.metaData[metaDataOffset++] = QFlags.class.isAssignableFrom(cls) ? 0x1 : 0x0;
	
	                    // Get the enum class
	                    Class<?> enumClass = Enum.class.isAssignableFrom(cls) ? cls : null;
	                    if (enumClass == null) {
	                        enumClass = getEnumForQFlags(cls);
	                    }
	
	                    enumConstantCount = enumClass.getEnumConstants().length;
	
	                    // Count
	                    metaData.metaData[metaDataOffset++] = enumConstantCount;
	
	                    // Data
	                    metaData.metaData[metaDataOffset++] = enumConstantOffset;
	
	                    enumConstantOffset += 2 * enumConstantCount;
	                } else if (!enclosingClass.isAssignableFrom(clazz) && !enclosingClasses.contains(enclosingClass.getName())) {
	                    // If the enclosing class of an enum is not in the current class hierarchy, then
	                    // the generated meta object needs to have a pointer to its meta object in the
	                    // extra-data.
	                    enclosingClasses.put(enclosingClass.getName(), enclosingClass);
	                }
                }
            }
            metaData.extraDataArray = enclosingClasses.values().toArray(new Class<?>[0]);

            // Enum constants
            for (Class<?> cls : classes) {

                if (cls.getEnclosingClass()!=null && cls.getEnclosingClass().equals(clazz)) {
                    // Get the enum class
                    Class<?> enumClass = Enum.class.isAssignableFrom(cls) ? cls : null;
                    if (enumClass == null) {
                        enumClass = getEnumForQFlags(cls);
                    }

                    Enum<?> enumConstants[] = (Enum[]) enumClass.getEnumConstants();

                    for (Enum<?> enumConstant : enumConstants) {
                        // Key
                        offset += addString(metaData.metaData, strings, stringsInOrder, enumConstant.name(), offset, metaDataOffset++);

                        // Value
                        metaData.metaData[metaDataOffset++] = enumConstant instanceof QtEnumerator
                                                              ? ((QtEnumerator) enumConstant).value()
                                                              : enumConstant.ordinal();
                    }
                }
            }
            
         // Constructors (### make sure enum types are covered, ### also test QFlags)
            for (int i=0; i<constructors.size(); ++i) {
            	Constructor<?> method = constructors.get(i);
            	
            	String javaMethodSignature = constructorSignature(method);
                metaData.originalSignatures[slots.size() + signalFields.size() + i] = javaMethodSignature;

                // Constructor signature
                offset += addString(metaData.metaData, strings, stringsInOrder, internalTypeNameOfMethodSignature(method, 1), offset, metaDataOffset++);

                // Constructor parameters
                offset += addString(metaData.metaData, strings, stringsInOrder, internalTypeNameOfParameters(method, 1), offset, metaDataOffset++);

                // Constructor type
                offset += addString(metaData.metaData, strings, stringsInOrder, "", offset, metaDataOffset++);

                // Constructor tag (Currently not supported by the moc either)
                offset += addString(metaData.metaData, strings, stringsInOrder, "", offset, metaDataOffset++);

                // Constructor flags
                int flags = MethodConstructor;
                int modifiers = method.getModifiers();
                if ((modifiers & Modifier.PRIVATE) == Modifier.PRIVATE)
                    flags |= MethodAccessPrivate;
                else if ((modifiers & Modifier.PROTECTED) == Modifier.PROTECTED)
                    flags |= MethodAccessProtected;
                else if ((modifiers & Modifier.PUBLIC) == Modifier.PUBLIC)
                    flags |= MethodAccessPublic;
                if(method.isAnnotationPresent(QtScriptable.class) && method.getAnnotation(QtScriptable.class).value()){
                	flags |= MethodScriptable;
                }

                metaData.metaData[metaDataOffset++] = flags;
            }
            metaData.constructorsArray = constructors.toArray(new Constructor[0]);

            // EOD
            metaData.metaData[metaDataOffset++] = 0;

            int stringDataOffset = 0;
            metaData.stringData = new byte[offset + 1];

            for (String s : stringsInOrder) {
                assert stringDataOffset == strings.get(s);
                System.arraycopy(s.getBytes(), 0, metaData.stringData, stringDataOffset, s.length());
                stringDataOffset += s.length();
                metaData.stringData[stringDataOffset++] = 0;
            }

        }

        return metaData;
    }

    // Using a variable to ensure this is changed in all the right places in the
    //  future when higher values are supported.
    public static final int METAOBJECT_REVISION_HIGHEST_SUPPORTED = 7;
    // This property allows you to override the QMetaObject revision number for
    //  QtJambi to use.
    public static final String K_com_trolltech_qt_qtjambi_metadata_revision = "com.trolltech.qt.qtjambi.metadata.revision";
    private static int revision;
    // This should be updated as the code-base supports the correct data layout
    //  for each new revision.  We don't necessarily have to support the features
    //  that new revision brings as well.
    private static int resolveMetaDataRevision() {
        int r = revision;
        if(r != 0)
            return r;

        int[] versionA = Utilities.getVersion();
        int major = -1;
        if(versionA.length > 0)
            major = versionA[0];
        int minor = -1;
        if(versionA.length > 1)
            minor = versionA[1];
        int plevel = -1;
        if(versionA.length > 2)
            plevel = versionA[2];
        // It became a requirement in 4.7.x to move away from revision 1
        //  in order to restore broken functionality due to improvements
        //  in Qt.  Before this time QtJambi always used to report
        //  revision=1.
        // The following is the default version for that version of Qt
        //  this is compatible with what QtJambi provides and needs to
        //  be updated with any future revision.
        if(major <= 3)
            r = 1;  // Good luck with getting QtJambi working!
        else if(major == 4 && minor <= 5)
            r = 1;  // historically this version was used
        else if(major == 4 && minor == 6)
            r = 4;  // 4.6.x (historically revision 1 was used)
        else if(major == 4 && minor == 7)
            r = 5;  // 4.7.x (known issues with 1 through 3, use revision 4 minimum, 5 is best)
        else if(major == 4)
            r = 6;  // 4.8.x
        else if(major == 5)
            r = 7;  // 5.0.x (Qt5 requires a minimum of this revision)
        else  // All future versions
            r = METAOBJECT_REVISION_HIGHEST_SUPPORTED;

        // The above computes the automatic default so we can report it below
        int revisionOverride = resolveMetaDataRevisionFromSystemProperty(r);
        if(revisionOverride > 0)
            r = revisionOverride;

        revision = r;
        return r;
    }

    /**
     * A facility to override the default metadata revision with a system property.
     * More useful for testing and fault diagnosis than any practical runtime purpose.
     * @param defaultRevision Value only used in error messages.
     * @return -1 on parse error, 0 when no configured, >0 is a configured value.
     */
    private static int resolveMetaDataRevisionFromSystemProperty(int defaultRevision) {
        int r = 0;
        String s = null;
        try {
            s = System.getProperty(K_com_trolltech_qt_qtjambi_metadata_revision);
            if(s != null) {
                r = Integer.parseInt(s);
                if(r <= 0 || r > METAOBJECT_REVISION_HIGHEST_SUPPORTED)
                    r = -1;  // invalidate causing the value to be ignored
            }
        } catch(NumberFormatException e) {
            r = -1;
        } catch(SecurityException e) {
            e.printStackTrace();
        }
        if(r < 0)
            System.err.println("System Property " + K_com_trolltech_qt_qtjambi_metadata_revision + " invalid value: " + s + " using default: " + defaultRevision);
        return r;
    }
    
    public static native void registerMetaType(Class<?> clazz);
}

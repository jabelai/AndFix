package com.alipay.euler.andfix.hook;

import android.util.Log;

import com.alipay.euler.andfix.AndFix;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hook Manager
 *
 * <p/>
 * Created by jabe on 6/1/16.
 */
public class HookManager {


    /**
     * [Key] = MethodName
     * [Value] = BackupMethods
     */
    private static final Map<String, Map<String, List<Method>>> classToBackupMethodsMapping = new ConcurrentHashMap<String, Map<String, List<Method>>>();

    @SuppressWarnings("ImplicitArrayToString")
    public static void applyHooks(Class<?> holdClass) {
        for (Method hookMethod : holdClass.getDeclaredMethods()) {
            Hook hook = hookMethod.getAnnotation(Hook.class);
            if (hook != null) {
                String statement = hook.value();
                String[] splitValues = statement.split("::");
                if (splitValues.length == 2) {
                    String className = splitValues[0];
                    String[] methodNameWithSignature = splitValues[1].split("@");
                    if (methodNameWithSignature.length <= 2) {
                        String methodName = methodNameWithSignature[0];
                        String signature = methodNameWithSignature.length == 2 ? methodNameWithSignature[1] : "";
                        String[] paramList = signature.split("#");
                        if (paramList[0].equals("")) {
                            paramList = new String[0];
                        }
                        try {
                            Class<?> clazz = Class.forName(className);
                            boolean isResolve = false;
                            for (Method method : clazz.getDeclaredMethods()) {
                                if (method.getName().equals(methodName)) {
                                    Class<?>[] types = method.getParameterTypes();
                                    if (paramList.length == types.length) {
                                        boolean isMatch = true;
                                        for (int N = 0; N < types.length; N++) {
                                            if (!types[N].getName().equals(paramList[N])) {
                                                isMatch = false;
                                                break;
                                            }
                                        }
                                        if (isMatch) {
                                            checkNeedBackup(holdClass, statement, method, hookMethod);
                                            replaceMethod(method, hookMethod);
                                            isResolve = true;
                                            Log.d("hook", "have hooked method : " + statement);
                                        }
                                    }
                                }
                                if (isResolve) {
                                    break;
                                }
                            }
                            if (!isResolve) {
                                Log.e("hook", "Cannot resolve Method " + methodNameWithSignature);
                            }
                        } catch (Throwable e) {
                            Log.e("hook", "Error to Load Hook Method From : " + hookMethod.getName());
                            e.printStackTrace();
                        }

                    } else {
                        Log.e("hook", "Can't split method and signature : " + methodNameWithSignature);
                    }
                } else {
                    Log.e("hook", "Can't understand your statement : "+ statement);
                }
            }
        }
    }

    private static void checkNeedBackup(Class<?> holdClass, String statement, Method method, Method hookMethod) {
        for (Method backupMethod : holdClass.getDeclaredMethods()) {
            HookBackup backup = backupMethod.getAnnotation(HookBackup.class);
            if (backup != null && backup.value().equals(statement)) {
                // need backup.
                replaceMethod(backupMethod, method);
                backupHookMethod(method, hookMethod, backupMethod);
            }
        }
    }

    private static void replaceMethod(Method method, Method hookMethod) {
        // here using andfix to hook .
        AndFix.addReplaceMethod(method, hookMethod);
    }

    private static void backupHookMethod(Method method, Method hookMethod, Method backupMethod) {
        Log.i("hook", "backup hook method : " + method.getDeclaringClass().getName() + "." + method.getName());
        String className = hookMethod.getDeclaringClass().getName();
        String methodName = method.getName();

        Map<String, List<Method>> methodNameToBackupMethodsMap = classToBackupMethodsMapping.get(className);
        if (methodNameToBackupMethodsMap == null) {
            methodNameToBackupMethodsMap = new ConcurrentHashMap<String, List<Method>>();
            classToBackupMethodsMapping.put(className, methodNameToBackupMethodsMap);
        }
        List<Method> backupList = methodNameToBackupMethodsMap.get(methodName);
        if (backupList == null) {
            backupList = new LinkedList<Method>();
            methodNameToBackupMethodsMap.put(methodName, backupList);
        }
        hookMethod.setAccessible(true);
        backupList.add(backupMethod);
    }


    public static <T> T callOrigin(Object who, Object... args) {
        StackTraceElement[] traceElements = Thread.currentThread().getStackTrace();
        StackTraceElement currentInvoking = traceElements[3];
        String invokingClassName = currentInvoking.getClassName();
        String invokingMethodName = currentInvoking.getMethodName();
        Log.i("hook", "call origin : " + invokingMethodName + " , " + invokingMethodName);
        Map<String, List<Method>> methodNameToBackupMethodsMap = classToBackupMethodsMapping.get(invokingClassName);
        if (methodNameToBackupMethodsMap != null) {
            List<Method> methodList = methodNameToBackupMethodsMap.get(invokingMethodName);
            if (methodList != null) {
                Method method = matchSimilarMethod(methodList, args);
                if (method != null) {
                    try {
                        return callOrigin(method, who, args);
                    } catch (Throwable e) {
                        Log.e("hook", "Call super method with error : " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    Log.e("hook", "Super method cannot found in backup map.");
                }
            }
        }
        Log.e("hook", "Super class cannot found in backup map.");
        return null;
    }

    private static Method matchSimilarMethod(List<Method> methodList, Object... args) {
        if (methodList.size() == 1) {
            //Only hold one method
            return methodList.get(0);
        } else {
            //Hold more than one methods
            Class<?>[] types = types(args);
            for (Method method : methodList) {
                if (isSimilarSignature(method.getParameterTypes(), types)) {
                    return method;
                }
            }
            return null;
        }
    }


    private static boolean isSimilarSignature(Class<?>[] declaredTypes, Class<?>[] actualTypes) {
        if (declaredTypes.length == actualTypes.length) {
            for (int i = 0; i < actualTypes.length; i++) {
                if (actualTypes[i] == NULL.class)
                    continue;
                if (wrap(declaredTypes[i]).isAssignableFrom(wrap(actualTypes[i])))
                    continue;

                return false;
            }

            return true;
        } else {
            return false;
        }
    }

    private static Class<?> wrap(Class<?> type) {
        if (type == null) {
            return null;
        } else if (type.isPrimitive()) {
            if (boolean.class == type) {
                return Boolean.class;
            } else if (int.class == type) {
                return Integer.class;
            } else if (long.class == type) {
                return Long.class;
            } else if (short.class == type) {
                return Short.class;
            } else if (byte.class == type) {
                return Byte.class;
            } else if (double.class == type) {
                return Double.class;
            } else if (float.class == type) {
                return Float.class;
            } else if (char.class == type) {
                return Character.class;
            } else if (void.class == type) {
                return Void.class;
            }
        }

        return type;
    }


    private static Class<?>[] types(Object... values) {
        if (values == null) {
            return new Class[0];
        }

        Class<?>[] result = new Class[values.length];

        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            result[i] = value == null ? NULL.class : value.getClass();
        }

        return result;
    }

    private static final class NULL {
    }


    @SuppressWarnings("unchecked")
    private static <T> T callOrigin(Method method, Object who, Object... args) throws Throwable {
        return (T) method.invoke(who, args);
    }
}

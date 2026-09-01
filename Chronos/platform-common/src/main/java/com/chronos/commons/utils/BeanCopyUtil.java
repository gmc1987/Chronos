package com.chronos.commons.utils;
 
 import java.beans.PropertyDescriptor;
 import java.util.HashSet;
 import java.util.Set;
 import org.springframework.beans.BeanUtils;
 import org.springframework.beans.BeanWrapperImpl;
 
 
 
 
 public class BeanCopyUtil
 {
   public static void copyNonNullProperties(Object source, Object target) {
     BeanUtils.copyProperties(source, target, getNullPropertyNames(source));
   }
   
   public static String[] getNullPropertyNames(Object source) {
     BeanWrapperImpl beanWrapperImpl = new BeanWrapperImpl(source);
     PropertyDescriptor[] pds = beanWrapperImpl.getPropertyDescriptors();
     Set<String> emptyNames = new HashSet<>();
     for (PropertyDescriptor pd : pds) {
       Object srcValue = beanWrapperImpl.getPropertyValue(pd.getName());
       if (srcValue == null) emptyNames.add(pd.getName()); 
     } 
     return emptyNames.<String>toArray(new String[0]);
   }
 }


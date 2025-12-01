package com.cmclinnovations.agent.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class TypeCastUtils {

  // Private constructor to prevent instantiation
  private TypeCastUtils() {
    throw new UnsupportedOperationException("This class cannot be instantiated!");
  }

  /**
   * Cast object to a list of the specified class.
   * 
   * @param obj        Target object to cast.
   * @param targetType Target object class within the list.
   */
  public static <T> List<T> castToListObject(Object obj, Class<T> targetType) throws ClassCastException {
    if (obj instanceof List) {
      List<?> listObj = (List<?>) obj;
      List<T> results = new ArrayList<>();
      for (Object item : listObj) {
        if (targetType.isInstance(item)) {
          results.add(targetType.cast(item));
        } else {
          throw new ClassCastException("List contains elements that cannot be cast to " + targetType.getName());
        }
      }
      return results;
    } else if (targetType.isInstance(obj)) {
      return List.of(targetType.cast(obj));
    }
    throw new IllegalArgumentException("Invalid input. Object is not a list.");
  }

  /**
   * Cast object to the specified class.
   * 
   * @param obj        Target object to cast.
   * @param targetType Target object class.
   */
  public static <T> T castToObject(Object obj, Class<T> targetType) throws ClassCastException {
    if (targetType.isInstance(obj)) {
      return targetType.cast(obj);
    } else {
      throw new ClassCastException("Object cannot be cast to " + targetType.getName());
    }
  }

  /**
   * Cast a list to their corresponding queue.
   * 
   * @param inputs Target list input.
   */
  public static <T> Queue<T> castListToQueue(List<T> inputs) {
    Queue<T> output = new ArrayDeque<>();
    output.addAll(inputs);
    return output;
  }
}
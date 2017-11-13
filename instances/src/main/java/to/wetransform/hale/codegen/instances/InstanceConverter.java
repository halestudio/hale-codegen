/*
 * Copyright (c) 2016 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     wetransform GmbH <http://www.wetransform.to>
 */

package to.wetransform.hale.codegen.instances;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.eclipse.equinox.nonosgi.registry.RegistryFactoryHelper;

import eu.esdihumboldt.hale.common.instance.model.Group;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.MutableGroup;
import eu.esdihumboldt.hale.common.instance.model.MutableInstance;
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultGroup;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstance;
import eu.esdihumboldt.hale.common.instance.model.impl.DefaultInstanceCollection;
import eu.esdihumboldt.hale.common.schema.model.ChildDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;
import to.wetransform.hale.codegen.model.ModelInfo;
import to.wetransform.hale.codegen.model.ModelObject;
import to.wetransform.hale.codegen.model.Multiple;
import to.wetransform.hale.codegen.model.Named;
import to.wetransform.hale.codegen.model.Value;

public class InstanceConverter {

  private final Map<Class<?>, List<Field>> fieldCache = new HashMap<>();

  public InstanceConverter() {
    super();

    // initialize registry
    RegistryFactoryHelper.getRegistry();
  }

  public InstanceCollection convert(Iterable<? extends ModelObject> objects, TypeIndex schema) throws IllegalArgumentException, IllegalAccessException {
    //XXX improvement: on demand conversion in stream?

    Collection<Instance> instances = new ArrayList<>();

    for (ModelObject obj : objects) {
      instances.add(convert(obj, schema));
    }

    return new DefaultInstanceCollection(instances);
  }

  public Iterable<? extends ModelObject> convert(InstanceCollection instances, ModelInfo model) throws InstantiationException, IllegalAccessException {
    //XXX improvement: on demand conversion in stream?

    Collection<ModelObject> objects = new ArrayList<>();
    try (ResourceIterator<Instance> it = instances.iterator()) {
      while (it.hasNext()) {
        Instance instance = it.next();

        QName typeName = instance.getDefinition().getName();
        Class<? extends ModelObject> modelClass = model.getModelClass(typeName);

        if (modelClass == null) {
          throw new IllegalStateException("Could not find model class for type " + typeName);
        }

        ModelObject object = convert(instance, modelClass);
        objects.add(object);
      }
    }
    return objects;
  }

  public Instance convert(ModelObject object, TypeIndex schema) throws IllegalArgumentException, IllegalAccessException {
    QName typeName = getName(object.getClass());
    TypeDefinition type = schema.getType(typeName);

    if (type == null) {
      throw new IllegalStateException("Type for model class not found in the given schema");
    }

    return convert(object, type);
  }

  private Instance convert(ModelObject object, TypeDefinition type) throws IllegalArgumentException, IllegalAccessException {
    MutableInstance result = new DefaultInstance(type, null);

    for (Field field : getAllFields(object.getClass())) {
      addFieldProperties(result, object, field);
    }

    return result;
  }

  private void addFieldProperties(MutableGroup result, Object object,
      Field field) throws IllegalArgumentException, IllegalAccessException {
    Object value = field.get(object);
    if (value == null) {
      // ignore null values
      return;
    }
    if (field.isAnnotationPresent(Multiple.class) && value instanceof Collection) {
      // ignore empty collection properties
      if (((Collection<?>) value).isEmpty()) {
        return;
      }
    }

    if (field.isAnnotationPresent(Value.class)) {
      // instance value
      if (result instanceof MutableInstance) {
        ((MutableInstance) result).setValue(value);
      }
      else {
        throw new IllegalStateException("Value field needs a mutable instance object to populate");
      }
    }
    else {
      // named property or group

      if (field.isAnnotationPresent(Multiple.class)) {
        // collection property
        if (!(value instanceof Collection<?>)) {
          throw new IllegalStateException("Wrong value type for collection property");
        }

        for (Object singleValue : (Collection<?>) value) {
          addFieldProperty(result, singleValue, field);
        }
      }
      else {
        // single property
        addFieldProperty(result, value, field);
      }
    }
  }

  private void addFieldProperty(MutableGroup result, Object value, Field field) throws IllegalArgumentException, IllegalAccessException {
    Named named = field.getAnnotation(Named.class);
    QName fieldName = new QName(named.namespace(), named.value());

    ChildDefinition<?> fieldDef = result.getDefinition().getChild(fieldName);
    if (fieldDef == null) {
      throw new IllegalStateException("Definition of field not found");
    }

    if (fieldDef.asProperty() != null) {
      // simple property or complex property

      if (value instanceof ModelObject) {
        // complex
        result.addProperty(fieldName, convert((ModelObject) value, fieldDef.asProperty().getPropertyType()));
      }
      else {
        // simple
        result.addProperty(fieldName, value);
      }
    }
    else {
      // assuming group/choice
      MutableGroup group = new DefaultGroup(fieldDef.asGroup());
      for (Field groupField : getAllFields(value.getClass())) {
        addFieldProperties(group, value, groupField);
      }
      result.addProperty(fieldName, group);
    }
  }

  private List<Field> getAllFields(Class<?> clazz) {
    List<Field> fields = fieldCache.get(clazz);
    if (fields != null) {
      return Collections.unmodifiableList(fields);
    }

    // collect fields
    fields = new ArrayList<>();

    // super class fields
    if (clazz.getSuperclass() != null) {
      fields.addAll(getAllFields(clazz.getSuperclass()));
    }

    // declared fields
    for (Field field : clazz.getDeclaredFields()) {
      if (field.isAnnotationPresent(Named.class) || field.isAnnotationPresent(Value.class)) {
        // only add if it's a Named or Value field
        field.setAccessible(true);
        fields.add(field);
      }
    }

    fieldCache.put(clazz, fields);
    return Collections.unmodifiableList(fields);
  }

  private QName getName(Class<? extends ModelObject> clazz) {
    Named named = clazz.getAnnotation(Named.class);
    if (named != null) {
      return new QName(named.namespace(), named.value());
    }
    throw new IllegalStateException("Class does not have a name annotation");
  }

  public <T extends ModelObject> T convert(Instance instance, Class<T> modelClass) throws InstantiationException, IllegalAccessException {
    T result = modelClass.newInstance();

    for (Field field : getAllFields(modelClass)) {
      setField(instance, result, field);
    }

    return result;
  }

  private void setField(Group parent, Object modelObject, Field field) throws IllegalArgumentException, IllegalAccessException, InstantiationException {
    if (field.isAnnotationPresent(Value.class)) {
      // instance value
      if (parent instanceof Instance) {
        Object value = ((Instance) parent).getValue();
        // can only be a simple value (no model or group class)
        field.set(modelObject, value);
      }
    }
    else {
      Named named = field.getAnnotation(Named.class);
      QName fieldName = new QName(named.namespace(), named.value());

      Object[] values = parent.getProperty(fieldName);
      if (values != null && values.length > 0) {
        for (Object value : values) {
          setFieldValue(value, modelObject, field);
        }
      }
    }
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private void setFieldValue(Object value, Object modelObject, Field field) throws InstantiationException, IllegalAccessException {
    // prepare value
    if (value instanceof Group) {
      // complex value field

      // determine value class
      Class<?> valueClass;
      if (field.isAnnotationPresent(Multiple.class)) {
        Type parameterType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
        valueClass = (Class<?>) parameterType;
      }
      else {
        valueClass = field.getType();
      }

      Object groupObject = valueClass.newInstance();

      for (Field groupField : getAllFields(valueClass)) {
        setField((Group) value, groupObject, groupField);
      }

      // use converted object
      value = groupObject;
    }
    else {
      // simple value field
      // -> nothing to do, using value as-is
    }

    // add/set field value
    if (field.isAnnotationPresent(Multiple.class)) {
      // add value to list
      Object list = field.get(modelObject);
      ((Collection) list).add(value);
    }
    else {
      // single value
      field.set(modelObject, value);
    }
  }

}

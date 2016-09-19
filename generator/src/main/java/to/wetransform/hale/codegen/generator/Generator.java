package to.wetransform.hale.codegen.generator;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.lang.model.element.Modifier;
import javax.xml.namespace.QName;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import eu.esdihumboldt.hale.common.schema.model.ChildDefinition;
import eu.esdihumboldt.hale.common.schema.model.PropertyDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.constraint.property.Cardinality;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.Binding;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.HasValueFlag;

public class Generator {

  private final Map<QName, ClassName> typeClasses = new HashMap<>();
  private File targetFolder;

  /**
   * Generate model classes for the given type definitions.
   *
   * @param types the types to generate model classes for
   * @param targetFolder the target folder in which to place the classes
   * @throws IOException
   */
  public void generateModel(Collection<? extends TypeDefinition> types, File targetFolder) throws IOException {
    this.targetFolder = targetFolder;

    for (TypeDefinition type : types) {
      getOrCreateClass(type);
    }
  }

  private ClassName getOrCreateClass(TypeDefinition type) throws IOException {
    ClassName className = typeClasses.get(type.getName());

    if (className != null) {
      // class was already generated
      return className;
    }

    // generate class
    className = newClassName(type.getName());
    TypeSpec.Builder builder = TypeSpec.classBuilder(className);

    // set super type
    if (type.getSuperType() != null && !isSimpleType(type)) {
      //TODO check if this kind of recursion can lead to problems (cycles!)
      builder.superclass(getOrCreateClass(type.getSuperType()));
    }

    if (isSimpleType(type)) {
      // value class that is used as super type and thus cannot be used
      // directly (e.g. subclassing String does not make sense)
      //FIXME magic property name 'value'
      Class<?> bindingClass = type.getConstraint(Binding.class).getBinding();
      addBeanProperty(builder, "value", ClassName.get(bindingClass));
    }
    else {
      // add properties
      addProperties(type, builder);
    }

    TypeSpec typeClass = builder.build();
    JavaFile javaFile = JavaFile.builder(className.packageName(), typeClass).build();

    javaFile.writeTo(targetFolder);

    typeClasses.put(type.getName(), className);
    return className;
  }

  private void addProperties(TypeDefinition type, TypeSpec.Builder builder) throws IOException {
    for (ChildDefinition<?> child : type.getDeclaredChildren()) {
      addProperty(builder, child);
    }
  }

  private void addProperty(TypeSpec.Builder builder, ChildDefinition<?> child) throws IOException {
    if (child.asProperty() != null) {
      PropertyDefinition property = child.asProperty();
      String propertyName = getPropertyName(property.getName());

      Cardinality card = property.getConstraint(Cardinality.class);
      //TODO handle cardinalities greater than one

      if (isSimpleType(property.getPropertyType())) {
        // simple type -> use binding
        Class<?> bindingClass = property.getPropertyType().getConstraint(Binding.class).getBinding();
        addBeanProperty(builder, propertyName, ClassName.get(bindingClass));
      }
      else {
        // complex type

        //TODO check if this kind of recursion can lead to problems (cycles!)
        ClassName propertyType = getOrCreateClass(property.getPropertyType());
        addBeanProperty(builder, propertyName, propertyType);
      }
    }
    else if (child.asGroup() != null) {
      //TODO
    }
    else {
      throw new IllegalStateException("Unsupported child definition type");
    }
  }

  private boolean isSimpleType(TypeDefinition propertyType) {
    return propertyType.getConstraint(HasValueFlag.class).isEnabled() &&
        propertyType.getChildren().isEmpty();
  }

  private void addBeanProperty(TypeSpec.Builder builder, String propertyName, TypeName propertyType) {
    // add the field
    builder.addField(propertyType, propertyName, Modifier.PRIVATE);

    // add setter
    String setterName = "set" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
    MethodSpec setter = MethodSpec.methodBuilder(setterName)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(propertyType, propertyName, Modifier.FINAL)
        .addStatement("this." + propertyName + " = " + propertyName)
        .build();
    builder.addMethod(setter);

    // add getter
    String getterName = "get" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
    MethodSpec getter = MethodSpec.methodBuilder(getterName)
        .addModifiers(Modifier.PUBLIC)
        .returns(propertyType)
        .addStatement("return this." + propertyName)
        .build();
    builder.addMethod(getter);
  }

  private String getPropertyName(QName name) {
    // TODO Auto-generated method stub
    return name.getLocalPart();
  }

  private ClassName newClassName(QName name) {
    // TODO Auto-generated method stub
    return ClassName.get("com.example", name.getLocalPart());
  }

}

package to.wetransform.hale.codegen.generator;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;
import javax.xml.namespace.QName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import eu.esdihumboldt.hale.common.instance.model.Group;
import eu.esdihumboldt.hale.common.schema.model.ChildDefinition;
import eu.esdihumboldt.hale.common.schema.model.DefinitionGroup;
import eu.esdihumboldt.hale.common.schema.model.GroupPropertyDefinition;
import eu.esdihumboldt.hale.common.schema.model.PropertyDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.constraint.property.Cardinality;
import eu.esdihumboldt.hale.common.schema.model.constraint.property.ChoiceFlag;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.Binding;
import eu.esdihumboldt.hale.common.schema.model.constraint.type.HasValueFlag;
import to.wetransform.hale.codegen.model.Choice;
import to.wetransform.hale.codegen.model.ModelObject;
import to.wetransform.hale.codegen.model.Multiple;
import to.wetransform.hale.codegen.model.Named;
import to.wetransform.hale.codegen.model.Property;
import to.wetransform.hale.codegen.model.Value;

public class Generator {

  private static final Logger log = LoggerFactory.getLogger(Generator.class);

  private final Map<QName, ClassName> typeClasses = new HashMap<>();
  private final Map<QName, ClassName> groupClasses = new HashMap<>();
  private final String packagePrefix = ""; //TODO configurable
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
    typeClasses.put(type.getName(), className); // being generated

    TypeSpec.Builder builder = TypeSpec.classBuilder(className);
    builder.addModifiers(Modifier.PUBLIC);

    // add named annotation
    builder.addAnnotation(createNameAnnotation(type.getName()));

    // set super type
    if (type.getSuperType() != null && !isSimpleType(type) &&
        // if the HasValueFlag is removed in the type, don't add the super type
        !(!type.getConstraint(HasValueFlag.class).isEnabled()
            && type.getSuperType().getConstraint(HasValueFlag.class).isEnabled())) {
      //TODO check if this kind of recursion can lead to problems (cycles!)
      builder.superclass(getOrCreateClass(type.getSuperType()));
    }
    else {
      // must implement Serializable
      builder.addSuperinterface(ClassName.get(Serializable.class));
      // add marker interface
      builder.addSuperinterface(ClassName.get(ModelObject.class));
    }

    if (isSimpleType(type)) {
      // value class that is used as super type and thus cannot be used
      // directly (e.g. subclassing String does not make sense)
      //FIXME magic property name 'value'
      Class<?> bindingClass = type.getConstraint(Binding.class).getBinding();
      addBeanProperty(builder, "value", ClassName.get(bindingClass), null);
    }
    else {
      // add properties
      addProperties(type, builder);
    }

    TypeSpec typeClass = builder.build();
    JavaFile javaFile = JavaFile.builder(className.packageName(), typeClass).build();

    javaFile.writeTo(targetFolder);

    return className;
  }

  private TypeName getOrCreateGroupType(GroupPropertyDefinition group) throws IOException {
    //XXX is there potential to reuse group types?
    //XXX identifier right choice for index
    ClassName className = groupClasses.get(group.getName());

    if (className != null) {
      // class was already generated
      return className;
    }

    groupClasses.put(group.getName(), className); // being generated...

    // generate class
    className = newClassName(group.getName()); //XXX is the name sufficient?
    TypeSpec.Builder builder = TypeSpec.classBuilder(className);
    builder.addModifiers(Modifier.PUBLIC);

    // add named annotation
    //XXX groups don't have resolvable names
//    builder.addAnnotation(createNameAnnotation(group.getName()));

    //TODO mark as group or choice?

    // add properties
    //XXX makes sense for a sequence, but can we provide a better interface for choices?
    addProperties(group, builder);

    TypeSpec typeClass = builder.build();
    JavaFile javaFile = JavaFile.builder(className.packageName(), typeClass).build();

    javaFile.writeTo(targetFolder);
    return className;
  }

  private AnnotationSpec createNameAnnotation(QName name) {
    if (name != null) {
      AnnotationSpec.Builder builder = AnnotationSpec.builder(Named.class);

      builder.addMember("value", "$S", name.getLocalPart());

      if (name.getNamespaceURI() != null && !name.getNamespaceURI().isEmpty()) {
        builder.addMember("namespace", "$S", name.getNamespaceURI());
      }

      return builder.build();
    }
    else {
      // special case - null name is interpreted as "value"
      return AnnotationSpec.builder(Value.class).build();
    }
  }

  private void addProperties(DefinitionGroup type, TypeSpec.Builder builder) throws IOException {
    //TODO special handling for restriction types? seems properties are repeatedly declared in subtypes there
    for (ChildDefinition<?> child : type.getDeclaredChildren()) {
      addProperty(builder, child);
    }
  }

  private void addProperty(TypeSpec.Builder builder, ChildDefinition<?> child) throws IOException {
    TypeName propertyType;
    String propertyName = getPropertyName(child.getName());
    Cardinality card;

    if (child.asProperty() != null) {
      PropertyDefinition property = child.asProperty();

      if (isSimpleType(property.getPropertyType())) {
        // simple type -> use binding
        Class<?> bindingClass = property.getPropertyType().getConstraint(Binding.class).getBinding();
        propertyType = ClassName.get(bindingClass);

      }
      else {
        // complex type
        //TODO check if this kind of recursion can lead to problems (cycles!)
        propertyType = getOrCreateClass(property.getPropertyType());
      }

      card = property.getConstraint(Cardinality.class);
    }
    else if (child.asGroup() != null) {
      GroupPropertyDefinition group = child.asGroup();

      //FIXME
      //XXX prefer display name? uniqueness?!
      //XXX for now only for choices on special conditions
      if (group.getConstraint(ChoiceFlag.class).isEnabled()) {
        String alternative = NameAllocator.toJavaIdentifier(group.getDisplayName());
        if (!alternative.contains("choice")) {
          propertyName = alternative;
        }
      }

      propertyType = getOrCreateGroupType(group);

      card = group.getConstraint(Cardinality.class);
    }
    else {
      throw new IllegalStateException("Unsupported child definition type");
    }

    if (card.mayOccurMultipleTimes()) {
      addCollectionProperty(builder, propertyName, propertyType, child);
    }
    else {
      addBeanProperty(builder, propertyName, propertyType, child);
    }
  }

  private boolean isSimpleType(TypeDefinition propertyType) {
    return propertyType.getConstraint(HasValueFlag.class).isEnabled() &&
        propertyType.getChildren().isEmpty();
  }

  private void addBeanProperty(TypeSpec.Builder builder, String propertyName, TypeName propertyType,
      ChildDefinition<?> definition) {
    QName qualifiedName = (definition == null) ? (null) : (definition.getName());

    // add the field
    FieldSpec.Builder fieldBuilder = FieldSpec.builder(propertyType, propertyName, Modifier.PRIVATE)
        .addAnnotation(createNameAnnotation(qualifiedName));
    if (definition != null) {
      fieldBuilder.addAnnotation(getPropertyAnnotation(definition));
    }
    builder.addField(fieldBuilder.build());

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

  private AnnotationSpec getPropertyAnnotation(ChildDefinition<?> definition) {
    Class<?> type = Property.class;

    if (definition.asGroup() != null) {
      if (definition.asGroup().getConstraint(ChoiceFlag.class).isEnabled()) {
        type = Choice.class;
      }
      else {
        type = Group.class;
      }
    }

    return AnnotationSpec.builder(type).build();
  }

  private void addCollectionProperty(TypeSpec.Builder builder, String propertyName, TypeName propertyType,
      ChildDefinition<?> definition) {
    QName qualifiedName = (definition == null) ? (null) : (definition.getName());

    propertyType = ParameterizedTypeName.get(ClassName.get(List.class), propertyType);

    // add the field
    FieldSpec.Builder fieldBuilder = FieldSpec.builder(propertyType, propertyName, Modifier.PRIVATE)
        // initialize with empty collection
        .initializer("new $T()", ClassName.get(ArrayList.class))
        .addAnnotation(Multiple.class)
        .addAnnotation(createNameAnnotation(qualifiedName));
    if (definition != null) {
      fieldBuilder.addAnnotation(getPropertyAnnotation(definition));
    }
    builder.addField(fieldBuilder.build());

    //TODO collection optimized methods like add()?

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
    //FIXME collisions in classes and their super classes need to be avoided
    // -> use scoped NameAllocator and add "value", then super type properties, then properties... ?
    return toValidIdentifier(name.getLocalPart());
  }

  private ClassName newClassName(QName name) {
    // TODO Auto-generated method stub
    String packageName = getPackageName(name.getNamespaceURI());
    if (packageName == null || packageName.isEmpty()) {
      packageName = packagePrefix;
    }
    else if (!packagePrefix.isEmpty()) {
      packageName = packagePrefix + "." + toValidIdentifier(packageName);
    }
    return ClassName.get(packageName, toValidIdentifier(name.getLocalPart()));
  }

  private String getPackageName(String namespaceURI) {
    List<String> parts = new ArrayList<>();

    // first try interpreting as URI
    try {
      URI uri = new URI(namespaceURI);
      if (uri.getHost() != null) {
        String[] hostParts = uri.getHost().split("\\.");
        for (int i = hostParts.length - 1; i >= 0; i--) {
          String part = hostParts[i];
          if (part != null && !part.isEmpty()) {
            parts.add(toValidIdentifier(part));
          }
        }
      }
      if (uri.getPath() != null) {
        String[] pathParts = uri.getPath().split("/");
        for (int i = 0; i < pathParts.length; i++) {
          String part = pathParts[i];
          if (part != null && !part.isEmpty()) {
            parts.add(toValidIdentifier(part.toLowerCase()));
          }
        }
      }
    } catch (Exception e) {
      //TODO fall back to removing illegal parameters?
      log.error("Error determining package name from namespace", e);
    }

    return parts.stream().collect(Collectors.joining("."));
  }

  private static final Set<String> reservedKeywords = new HashSet<>();
  static {
    reservedKeywords.add("abstract");
    reservedKeywords.add("assert");
    reservedKeywords.add("boolean");
    reservedKeywords.add("break");
    reservedKeywords.add("byte");
    reservedKeywords.add("case");
    reservedKeywords.add("catch");
    reservedKeywords.add("char");
    reservedKeywords.add("class");
    reservedKeywords.add("const");
    reservedKeywords.add("continue");
    reservedKeywords.add("default");
    reservedKeywords.add("do");
    reservedKeywords.add("double");
    reservedKeywords.add("else");
    reservedKeywords.add("extends");
    reservedKeywords.add("false");
    reservedKeywords.add("final");
    reservedKeywords.add("finally");
    reservedKeywords.add("float");
    reservedKeywords.add("for");
    reservedKeywords.add("goto");
    reservedKeywords.add("if");
    reservedKeywords.add("implements");
    reservedKeywords.add("import");
    reservedKeywords.add("instanceof");
    reservedKeywords.add("int");
    reservedKeywords.add("interface");
    reservedKeywords.add("long");
    reservedKeywords.add("native");
    reservedKeywords.add("new");
    reservedKeywords.add("null");
    reservedKeywords.add("package");
    reservedKeywords.add("private");
    reservedKeywords.add("protected");
    reservedKeywords.add("public");
    reservedKeywords.add("return");
    reservedKeywords.add("short");
    reservedKeywords.add("static");
    reservedKeywords.add("strictfp");
    reservedKeywords.add("super");
    reservedKeywords.add("switch");
    reservedKeywords.add("synchronized");
    reservedKeywords.add("this");
    reservedKeywords.add("throw");
    reservedKeywords.add("throws");
    reservedKeywords.add("transient");
    reservedKeywords.add("true");
    reservedKeywords.add("try");
    reservedKeywords.add("void");
    reservedKeywords.add("volatile");
    reservedKeywords.add("while");
  }

  private String toValidIdentifier(String ident) {
    String result = NameAllocator.toJavaIdentifier(ident);

    // reserved keywords not properly handled
    if (reservedKeywords.contains(result)) {
      return "_" + result;
    }

    return result;
  }

}

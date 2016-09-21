package to.wetransform.hale.codegen.model;

import javax.xml.namespace.QName;

public interface ModelInfo {

  Class<? extends ModelObject> getModelClass(QName name);

}

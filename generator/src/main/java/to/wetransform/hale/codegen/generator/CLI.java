package to.wetransform.hale.codegen.generator;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;

import org.eclipse.equinox.nonosgi.registry.RegistryFactoryHelper;

import eu.esdihumboldt.hale.common.core.io.IOProviderConfigurationException;
import eu.esdihumboldt.hale.common.core.io.report.IOReport;
import eu.esdihumboldt.hale.common.core.io.supplier.DefaultInputSupplier;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.io.xsd.reader.XmlSchemaReader;

public class CLI {

  public static void main(String[] args) throws IOProviderConfigurationException, IOException {
    URI schema = fileOrUri(args[0]);
    File targetFolder = new File(args[1]);

    run(schema, targetFolder);
  }

  public static void run(URI schema, File targetFolder) throws IOProviderConfigurationException, IOException {
    // initialize HALE registry
    RegistryFactoryHelper.getRegistry();

    // load XML Schema
    XmlSchemaReader reader = new XmlSchemaReader();
    reader.setSource(new DefaultInputSupplier(schema));
    reader.setOnlyElementsMappable(true);
    IOReport report = reader.execute(null);
    if (!report.isSuccess()) {
      throw new IllegalStateException("Loading XML schema failed");
    }

    Collection<? extends TypeDefinition> types = reader.getSchema().getMappingRelevantTypes();

    Generator generator = new Generator();
    generator.generateModel(types, targetFolder);
  }

  /**
   * Create an URI from a String that is a file or URI.
   *
   * @param value the string value
   * @return the URI
   */
  public static URI fileOrUri(String value) {
    try {
      URI uri = URI.create(value);
      if (uri.getScheme() != null && uri.getScheme().length() > 1) {
        // only accept as URI if a schema is present
        // and the scheme is more than just one character
        // which is likely a Windows drive letter
        return uri;
      }
      else {
        return new File(value).toURI();
      }
    } catch (Exception e) {
      return new File(value).toURI();
    }
  }

}

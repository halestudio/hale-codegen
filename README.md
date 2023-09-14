# hale-codegen

Code generator for model classes based on the hale schema model.

The project consists of three modules:

- **model** - Annotations and helper/base classes for generated models.
- **generator** - Code generator for models based on a schema that can be read by hale, includes a basic command line interface.
- **instances** - Convert model objects to and from instances that can be read/written with the hale API. This indirectly allows reading/writing model objects, e.g. from/to XML or GML files

An example project using the generator to generate classes, read and write data can be found [here](https://github.com/halestudio/hale-codegen-example).

## Project status

This project is a **proof of concept** which means that it was implemented to cover a specific use case to a certain extent and comes with some limitations.

Implementation notes:

- During generation, classes are generated for all types classified by hale as "mapping relevant types" by default, plus all corresponding dependencies. In principle, you can also restrict the output types to a certain selection of types (but this is currently not supported in the CLI).
- There was not much time to invest in the special handling of geometries - the JTS geometries are currently located in the properties explicitly marked as geometry properties (type GeometryProperty), as in hale. Best to look into the "SimpleWriteRead" test (example project) for an example.
- For reading or writing GML with the hale API it is always necessary to load the corresponding schema as well. The reading/writing of the model objects works in a way that they are converted from or to hale `Instance` objects. The meta information stored in the model classes via annotations is used for this purpose. This was the fastest approach to implement in the PoC. One advantage is that the procedure is independent of the type of schema, so one could also process data from a database, for example.
- In the hierarchy of the generated model classes there are still problems with certain XML schema constructs which can lead to conflicts, therefore for the generation of the 3A model approx. 10 classes are excluded which lead to compiler problems. In other places the problem occurs partly also, but should not limit the functionality.
- Generally there is still much potential to improve the naming of classes and characteristics, particularly with anonymous types and Choices/Sequences defined in the XML Schema. In principle, a prefix is provided for the package name, but is not supported in the CLI.

## Usage

### CLI

The code generator command line interface is very simple and does not allow for a lot of options currently.
It takes a schema URI (only XML Schema supported for the CLI) and a target folder where to put the generated classes.

```
generator <uri-to-schema> <target-folder>
```

For further development it probably makes sense to instead include a command into [hale-cli](https://github.com/halestudio/hale-cli), where existing mechanisms for loading a schema and providing options can be used.

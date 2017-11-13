# hale-codegen

Code generator for model classes based on the hale schema model.

The project consists of three modules:

- **model** - Annotations and helper/base classes for generated models.
- **generator** - Code generator for models based on a schema that can be read by hale, includes a basic command line interface.
- **instances** - Convert model objects to and from instances that can be read/written with the hale API. This indirectly allows reading/writing model objects, e.g. from/to XML or GML files

## Usage

### CLI

The code generator command line interface is very simple and does not allow for a lot of options currently.
It takes a schema URI (only XML Schema supported for the CLI) and a target folder where to put the generated classes.

```
generator <uri-to-schema> <target-folder>
```

For further development it probably makes sense to instead include a command into [hale-cli](https://github.com/halestudio/hale-cli), where existing mechanisms for loading a schema and providing options can be used.

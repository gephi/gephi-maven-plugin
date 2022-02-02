# Gephi Maven Plugin

[![build](https://github.com/gephi/gephi-maven-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/gephi/gephi-maven-plugin/actions/workflows/build.yml)

This Maven plugin assists Gephi plugins developers and is designed to be used along with the [gephi-plugins](https://github.com/gephi/gephi-plugins) repository and its instructions.

This plugin can help developers in the following way:

- **Validate** plugin against standards (e.g. check dependency version, configuration)

- **Run** a testing version of Gephi with developed plugins pre-installed

- **Generate** a skeleton plugin with the right folder structure and required configuration

## How it works

This plugin is designed to work in a repository that has forked the `gephi-plugins` master branch. Such a repository typically contains a `modules` folder where plugins are located.

Each Gephi plugin module can define dependencies to regular Java libraries, Netbeans modules and of course Gephi modules.

This plugin supports two types of plugin configurations: **single module** and **suite**.

### Single module

This is the simplest type of plugin and only contains a single module. It means there's only a single sub-folder in `modules` and a single entry in the `<modules>` configuration in `pom.xml`. Only a single NBM file is produced.

### Suite

A suite is defined by a collection of modules, which have dependencies between each other. A plugin can therefore be composed of multiple modules. Multiple NBM files are produced but the final suite is archived into a single ZIP file.

Suites, however need to designate a module that acts as the module definition, which has the plugin metadata (e.g. license, author) and dependencies to the other modules. For instance, a plugin with 3 modules `A`, `B` and `C` could define `A` as the principal module and adds `B` and `C` to the list of modules it depends on.

## Goals

### mvn org.gephi:gephi-maven-plugin:validate

This command is automatically run when working on the `gephi-plugins` repository and does the following checks:

- Checks the `gephi.version` parameter in each of the plugin modules matches with the version defined by `gephi-plugins`. The latter should be configured to the latest stable version of Gephi.

- Checks the project lists a license in its configuration.

- Checks the project lists an author in its configuration. 

- Checks the manifest contains a `OpenIDE-Module-Name` entry. This is the branding name of the plugin and should be filled.

- Checks the manifest contains the `OpenIDE-Module-Short-Description`, `OpenIDE-Module-Long-Description` and `OpenIDE-Module-Display-Category` entries. 

- Checks the `OpenIDE-Module-Display-Category` entry is one of the following value: "Layout", "Export", "Import", "Data Laboratory", "Filter", "Generator", "Metric", "Preview", "Tool", "Appearance", "Clustering" or "Other Category".

### mvn org.gephi:gephi-maven-plugin:run

This command runs a version of Gephi with the plugins pre-installed. This only works after the plugins have been built (i.e. by running `mvn package` on the repository).

The command accepts a `run.params.debug` parameter that allows to run Gephi with debug flags. The value string is directly passed as parameters to the application.

### mvn org.gephi:gephi-maven-plugin:generate

This command is an interactive plugin generation tool. It asks a few questions through the console and then generates the plugin folder structure and configuration files.

This tool only supports single module plugins at the moment. However, it's easy to extend into a suite by adding additional folders.

### mvn org.gephi:gephi-maven-plugin:migrate

This command is custom-built to migrate ant-based plugins to Maven and takes care of copying configuration, sources and resources files. It looks for ant-based plugin folders in the current directory and creates the appropriate plugin folders in `modules`.

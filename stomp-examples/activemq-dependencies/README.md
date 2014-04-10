<!--
/*
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version. 
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
-->

# Get Active MQ  Dependencies


## Introduction
This POM pulls in Active MQ dependencies required for ActiveMQ to run. DDF-STOMP has a dependency on ActiveMQ. The DDF framework does not include ActiveMQ by default. 
The DDF framework also does not allow for the addition of external repositories by default. Thus this POM allows Maven to pull in the necessary dependencies into the local repository to allow DDF-STOMP to install 
ActiveMQ and then start the core application.

## Running The POM

Simply type in 'mvn install' within the POM directory. This should pull all of the necessary dependencies for ActiveMQ into the local Maven repository.

## Additional information

Discussions can be found on the DDF area -- [Announcements forum](http://groups.google.com/group/ddf-announcements),  [Users forum](http://groups.google.com/group/ddf-users), and  [Developers forum](http://groups.google.com/group/ddf-developers).

If you find any issues, please submit reports with [JIRA](https://tools.codice.org/jira/browse/DDF).

For information on contributing see [Contributing to Codice](http://www.codice.org/contributing).

The DDF Website also contains additional information at [http://ddf.codice.org](http://ddf.codice.org).

-- The Codice Development Team

## Copyright / License
Copyright (c) Codice Foundation
 
This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License 
as published by the Free Software Foundation, either version 3 of the License, or any later version. 
 
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public License is distributed along with this program and can be found at
<http://www.gnu.org/licenses/lgpl.html>.
 

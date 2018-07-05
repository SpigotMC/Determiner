Determiner
========

Maven plugin which replaces usages of non deterministic classes with deterministic equivalents.

Code based on [Simulare](https://github.com/SpigotMC/Simulare).


Example Configuration
---------------------

```xml
<plugin>
    <groupId>net.md-5</groupId>
    <artifactId>determiner</artifactId>
    <version>0.1</version>
    <executions>
        <execution>
            <phase>process-classes</phase>
            <goals>
                <goal>transform</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

package com.hongjie.pms.architecture;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;
class ModuleLayoutTest {
    private static final Path ROOT=Path.of("src/main/java");
    @Test void sourcePathsMatchDeclaredPackagesAndRetiredRootPackagesStayAbsent()throws Exception{
        var packageLine=Pattern.compile("(?m)^package ([a-zA-Z0-9_.]+);");
        try(var files=Files.walk(ROOT)){
            for(var file:files.filter(p->p.toString().endsWith(".java")).toList()){
                var match=packageLine.matcher(Files.readString(file));assertThat(match.find()).as(file.toString()).isTrue();
                assertThat(file.getParent()).isEqualTo(ROOT.resolve(match.group(1).replace('.','/')));
                assertThat(match.group(1)).doesNotStartWith("com.hongjie.pms.refactor").doesNotStartWith("com.hongjie.pms.common").doesNotStartWith("com.hongjie.pms.AI");
            }
        }
    }
    @Test void platformAndSharedHaveNoBusinessModuleDependency()throws Exception{
        var reference=Pattern.compile("com\\.hongjie\\.pms\\.(modules|bootstrap)\\.");
        for(String layer:List.of("platform","shared")){
            try(var files=Files.walk(ROOT.resolve("com/hongjie/pms/"+layer))){
                for(var file:files.filter(p->p.toString().endsWith(".java")).toList()){
                    var code=Files.readString(file).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
                    // TraceAspect uses a package expression to instrument business services, not a class dependency.
                    if(file.getFileName().toString().equals("TraceAspect.java"))continue;
                    assertThat(reference.matcher(code).find()).as(file.toString()).isFalse();
                }
            }
        }
    }
    @Test void crossModuleInternalDependenciesCannotGrowSilently()throws Exception{
        var baseline=com.alibaba.fastjson2.JSON.parseArray(Files.readString(Path.of("docs/architecture/module-internal-dependencies.json")));
        Set<String> allowed=new HashSet<>();
        for(int i=0;i<baseline.size();i++){var row=baseline.getJSONObject(i);allowed.add(row.getString("source")+" -> "+row.getString("target"));}
        var packageLine=Pattern.compile("(?m)^package com\\.hongjie\\.pms\\.modules\\.([a-z]+)\\.");
        var reference=Pattern.compile("com\\.hongjie\\.pms\\.modules\\.([a-z]+)\\.[A-Za-z0-9_.$]+");
        try(var files=Files.walk(ROOT.resolve("com/hongjie/pms/modules"))){
            for(var file:files.filter(p->p.toString().endsWith(".java")).toList()){
                var text=Files.readString(file);var owner=packageLine.matcher(text);if(!owner.find())continue;
                var code=text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
                var targets=reference.matcher(code);
                while(targets.find()){
                    if(owner.group(1).equals(targets.group(1))||targets.group().contains(".api."))continue;
                    var key=ROOT.relativize(file).toString().replace('\\','/')+" -> "+targets.group();
                    assertThat(allowed).as("Expose a module API instead of adding an internal dependency: "+key).contains(key);
                }
            }
        }
    }
}

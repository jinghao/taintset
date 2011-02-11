javac javax/security/TaintSet.java
javac java/lang/*.java
jar uf vm.jar java/lang/*.class javax/security/*.class

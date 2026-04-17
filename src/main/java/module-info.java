module com.example.testgame {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires com.almasb.fxgl.all;

    opens com.example.testgame to javafx.fxml, com.almasb.fxgl.all;
    opens assets.textures to com.almasb.fxgl.all;
    exports com.example.testgame;
}
package com.example.testgame;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.EntityFactory;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.Spawns;
import com.almasb.fxgl.entity.components.CollidableComponent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import static com.almasb.fxgl.dsl.FXGL.*;

public class SwordGameFactory implements EntityFactory {

    @Spawns("player")
    public Entity newPlayer(SpawnData data) {
        return entityBuilder(data)
                .bbox(new com.almasb.fxgl.physics.HitBox(new javafx.geometry.Point2D(0, 0), com.almasb.fxgl.physics.BoundingShape.box(40, 40)))
                .with(new CollidableComponent(true))
                .build();
    }

    @Spawns("sword")
    public Entity newSword(SpawnData data) {
        Rectangle rect = new Rectangle(30, 5, Color.GOLD);
        return entityBuilder(data)
                .viewWithBBox(rect)
                .with(new CollidableComponent(true))
                .build();
    }
}

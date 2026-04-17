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
                .with(new PlayerComponent())
                .build();
    }

    @Spawns("sword")
    public Entity newSword(SpawnData data) {
        return entityBuilder(data)
                .bbox(new com.almasb.fxgl.physics.HitBox(new javafx.geometry.Point2D(0, 0), com.almasb.fxgl.physics.BoundingShape.box(30, 5)))
                .with(new CollidableComponent(true))
                .build();
    }

    @Spawns("parry_shield")
    public Entity newParryShield(SpawnData data) {
        // Larger box: 80x60 (compared to player's 40x40)
        return entityBuilder(data)
                .bbox(new com.almasb.fxgl.physics.HitBox(new javafx.geometry.Point2D(0, 0), com.almasb.fxgl.physics.BoundingShape.box(80, 60)))
                .with(new CollidableComponent(true))
                .build();
    }
}

package com.example.walletService.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="asset_types")
@Getter
@Setter
public class AssetType {
    @Id
    @GeneratedValue
    private Long id;

    private String name;
}

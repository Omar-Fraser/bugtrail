package com.omarfraser.bugtrail.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A part of a project that a defect can be filed against -- authentication,
 * the ticket board, the webhook receiver.
 */

@Entity
@Table(name = "component")
public class Component {
    
}
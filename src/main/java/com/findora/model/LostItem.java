package com.findora.model;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "lostitems")
public class LostItem extends Item {}

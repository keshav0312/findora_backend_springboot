package com.findora.model;

import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "founditems")
public class FoundItem extends Item {}

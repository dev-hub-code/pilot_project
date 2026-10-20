package com.sealease.backend.container.entity;

/** ISO container types offered on the platform. */
public enum ContainerType {
	DRY_10FT("10ft dry"),
	DRY_20FT("20ft dry"),
	DRY_40FT("40ft dry"),
	HIGH_CUBE_40FT("40ft high cube"),
	HIGH_CUBE_45FT("45ft high cube"),
	REEFER_20FT("20ft reefer"),
	REEFER_40FT("40ft reefer"),
	OPEN_TOP_20FT("20ft open top"),
	OPEN_TOP_40FT("40ft open top"),
	FLAT_RACK_20FT("20ft flat rack"),
	FLAT_RACK_40FT("40ft flat rack"),
	TANK_20FT("20ft tank");

	private final String label;

	ContainerType(String label) {
		this.label = label;
	}

	/** For messages to people, e.g. "20ft dry". */
	public String label() {
		return label;
	}

	/** The name of a container listing of this type, e.g. "20ft Dry container". */
	public String listingName() {
		String[] words = label.split(" ");
		StringBuilder name = new StringBuilder(words[0]);
		for (int i = 1; i < words.length; i++) {
			name.append(' ').append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
		}
		return name.append(" container").toString();
	}
}

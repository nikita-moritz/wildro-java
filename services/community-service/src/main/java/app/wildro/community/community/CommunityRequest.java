package app.wildro.community.community;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A community as its owner writes it. The database checks the same bounds once more. */
public record CommunityRequest(@NotBlank @Size(min = 2, max = 80) String name,
		@NotBlank @Size(max = 5000) String description, @NotBlank @Size(min = 2, max = 120) String area,
		@NotNull @Pattern(regexp = "de|en|ru") String locale,
		@Size(max = 5) List<@NotBlank @Size(max = 500) @Pattern(regexp = "https://\\S+") String> links) {

	List<String> linkList() {
		return this.links == null ? List.of() : this.links.stream().map(String::strip).toList();
	}

}

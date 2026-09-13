package com.chronos.education.scheduling.model.dto;
import jakarta.validation.constraints.*; import java.util.*;
public final class CollaborationDtos {
 private CollaborationDtos(){}
 public record PreparationCreateRequest(@NotBlank String offeringId,@NotBlank String title,@NotBlank String preparationType,String scheduleEntryId){}
 public record MemberInviteRequest(@NotBlank String teacherId,String role){}
 public record MemberResponseRequest(@NotBlank String response,String comment){}
 public record MaterialRequest(@NotBlank String title,@NotBlank String fileId,String metadataJson){}
 public record CommentRequest(@NotBlank String content){}
 public record ConclusionRequest(@NotBlank String conclusion,@NotBlank String lessonPlanId){}
 public record ResourceCreateRequest(@NotBlank String offeringId,@NotBlank String title,String shareScope,String materialType){}
 public record VersionRequest(@NotBlank String fileId,String metadataJson){}
}

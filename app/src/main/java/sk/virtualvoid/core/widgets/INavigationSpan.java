package sk.virtualvoid.core.widgets;

public interface INavigationSpan {
    String getUrl();

    Long getDiscussionId();
    Long getPostId();
    Long getMailId();
    boolean isImage();
    boolean isNavigation();
    boolean isMailMessage();
}

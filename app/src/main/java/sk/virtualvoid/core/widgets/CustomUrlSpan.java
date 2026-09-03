package sk.virtualvoid.core.widgets;

import sk.virtualvoid.nyxdroid.v2.internal.NavigationHandler;
//import android.os.Parcel;
//import android.text.ParcelableSpan;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;
//import android.view.ViewParent;

/**
 * @author Juraj
 */
public class CustomUrlSpan extends ClickableSpan implements INavigationSpan {
    private String url;
    private Long discussionId;
    private Long postId;
    private Long mailId;

    private boolean isImage;
    private boolean isNavigation;
    private boolean isMail;

    public CustomUrlSpan(String url) {
        this.url = url;
        this.isImage = false;
    }

    public CustomUrlSpan(String url, long discussionId, Long postId) {
        this.url = url;
        this.discussionId = discussionId;
        this.postId = postId;
        this.isNavigation = true;
    }

    public CustomUrlSpan(String url, boolean isImage, boolean isMail, long mailId) {
        this.url = url;
        this.isImage = isImage;
        this.isMail = isMail;
        this.mailId = mailId;
    }


    @Override
    public void updateDrawState(@NonNull TextPaint ds) {
        super.updateDrawState(ds);

        // zvyraznenie odpovede na prispevok, lepsi hack ma uz nenapadol, sorry, -vv-
        // me to taky lepe nenapadlo, Toomix
        if ((discussionId != null && postId != null) || mailId != null) {
            ds.setTypeface(Typeface.DEFAULT_BOLD);
        }
    }

    @Override
    public void onClick(View view) {
        View parent = (View) view.getParent();
        Object parentTag = parent.getTag();

        NavigationHandler navigation = new NavigationHandler(this, parentTag, view.getContext());
        navigation.doNavigation();
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public Long getDiscussionId() {
        return discussionId;
    }

    @Override
    public Long getPostId() {
        return postId;
    }

    public Long getMailId() {
        return mailId;
    }

    @Override
    public boolean isImage() {
        return isImage;
    }

    @Override
    public boolean isNavigation() {
        return isNavigation;
    }

    public boolean isMailMessage() {
        return isMail;
    }
}

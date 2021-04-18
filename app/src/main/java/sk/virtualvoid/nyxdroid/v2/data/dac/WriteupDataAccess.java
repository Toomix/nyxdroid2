package sk.virtualvoid.nyxdroid.v2.data.dac;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import sk.virtualvoid.core.NyxException;
import sk.virtualvoid.core.Task;
import sk.virtualvoid.core.TaskListener;
import sk.virtualvoid.core.TaskWorker;
import sk.virtualvoid.net.nyx.Connector;
import sk.virtualvoid.nyxdroid.library.Constants;
import sk.virtualvoid.nyxdroid.v2.data.BasePoco;
import sk.virtualvoid.nyxdroid.v2.data.BaseResponse;
import sk.virtualvoid.nyxdroid.v2.data.Context;
import sk.virtualvoid.nyxdroid.v2.data.NullResponse;
import sk.virtualvoid.nyxdroid.v2.data.SuccessResponse;
import sk.virtualvoid.nyxdroid.v2.data.UserActivity;
import sk.virtualvoid.nyxdroid.v2.data.WaitingFile;
import sk.virtualvoid.nyxdroid.v2.data.Writeup;
import sk.virtualvoid.nyxdroid.v2.data.WriteupBookmarkResponse;
import sk.virtualvoid.nyxdroid.v2.data.WriteupHomeResponse;
import sk.virtualvoid.nyxdroid.v2.data.WriteupResponse;
import sk.virtualvoid.nyxdroid.v2.data.query.WriteupBookmarkQuery;
import sk.virtualvoid.nyxdroid.v2.data.query.WriteupQuery;
import sk.virtualvoid.nyxdroid.v2.internal.VotingInfoResult;
import sk.virtualvoid.nyxdroid.v2.internal.VotingResponse;
import sk.virtualvoid.nyxdroid.v2.internal.VotingResult;
import sk.virtualvoid.nyxdroid.v2.internal.WriteupBookmarkQueryType;

import android.app.Activity;

/**
 * @author Juraj
 */
public class WriteupDataAccess {
    private final static Logger log = Logger.getLogger(WriteupDataAccess.class);

    public static Task<WriteupQuery, SuccessResponse<WriteupResponse>> getWriteups(Activity context, TaskListener<SuccessResponse<WriteupResponse>> listener) {
        return new Task<WriteupQuery, SuccessResponse<WriteupResponse>>(context, new GetWriteupsTaskWorker(), listener);
    }

    public static Task<WriteupQuery, NullResponse> sendWriteup(Activity context, TaskListener<NullResponse> listener) {
        return new Task<WriteupQuery, NullResponse>(context, new SendWriteupTaskWorker(), listener);
    }

    public static Task<WriteupQuery, VotingResponse> giveRating(Activity context, TaskListener<VotingResponse> listener) {
        return new Task<WriteupQuery, VotingResponse>(context, new RateWriteupTaskWorker(), listener);
    }

    public static Task<WriteupQuery, VotingInfoResult> getRatingInfo(Activity context, TaskListener<VotingInfoResult> listener) {
        return new Task<WriteupQuery, VotingInfoResult>(context, new RatingOverviewTaskWorker(), listener);
    }

    public static Task<WriteupQuery, NullResponse> reminder(Activity context, TaskListener<NullResponse> listener) {
        return new Task<WriteupQuery, NullResponse>(context, new ReminderTaskWorker(), listener);
    }

    public static Task<WriteupQuery, NullResponse> delete(Activity context, TaskListener<NullResponse> listener) {
        return new Task<WriteupQuery, NullResponse>(context, new DeleteTaskWorker(), listener);
    }

    public static Task<WriteupQuery, WriteupHomeResponse> getHome(Activity context, TaskListener<WriteupHomeResponse> listener) {
        return new Task<WriteupQuery, WriteupHomeResponse>(context, new GetHomeTaskWorker(), listener);
    }

    public static Task<WriteupBookmarkQuery, WriteupBookmarkResponse> bookOrUnbookWriteup(Activity context, TaskListener<WriteupBookmarkResponse> listener) {
        return new Task<WriteupBookmarkQuery, WriteupBookmarkResponse>(context, new BookOrUnbookWriteupTaskWorker(), listener);
    }

    public static class GetWriteupsTaskWorker extends TaskWorker<WriteupQuery, SuccessResponse<WriteupResponse>> {
        @Override
        public SuccessResponse<WriteupResponse> doWork(WriteupQuery input) throws NyxException {
            WriteupResponse result = new WriteupResponse();
            Context context = null;

            Connector connector = new Connector(getContext());

            // older posts (scrolling down)
            String baseUrl = "/discussion/" + input.Id;

            if (input.Direction == Constants.WriteupDirection.WRITEUP_DIRECTION_OLDER && input.LastId != null) {
                baseUrl = baseUrl + "?order=older_than&from_id=" + input.LastId;
            }

            // replies to particular post
            if (input.Direction == Constants.WriteupDirection.WRITEUP_DIRECTION_NEWER && input.TempId != null) {
                baseUrl = baseUrl + "?order=newer_than&from_id=" + input.LastId;
            }

            if (input.isFilterUser()) {
                if (!baseUrl.contains("?")) {
                    baseUrl = baseUrl + "?";
                } else {
                    baseUrl = baseUrl + "&";
                }
                baseUrl = baseUrl + "user=" + input.FilterUser;
            }
            if (input.isFilterContents()) {
                if (!baseUrl.contains("?")) {
                    baseUrl = baseUrl + "?";
                } else {
                    baseUrl = baseUrl + "&";
                }
                baseUrl = baseUrl + "text=" + input.FilterContents;
            }

            JSONObject root = connector.get(baseUrl);
            if (root == null) {
                throw new NyxException("Json result was null ?");
            } else {
                try {
                    if (root.has("discussion_common") && !root.isNull("discussion_common")) {
                        JSONObject discussionCommon = root.getJSONObject("discussion_common");
                        JSONObject discussion = discussionCommon.getJSONObject("discussion");

                        result.Id = discussion.getLong("id");
                        result.Name = discussion.getString("name_static");

                        result.CanWrite = discussion.getBoolean("ar_write");
                        result.CanDelete = discussion.getBoolean("ar_delete");

                        if (discussionCommon.has("bookmark") && !discussionCommon.isNull("bookmark")) {
                            JSONObject bookmark = discussionCommon.getJSONObject("bookmark");

                            result.Booked = bookmark.getBoolean("bookmark");
                        }
                    }

                    if (root.has("posts") && !root.isNull("posts")) {
                        result.Writeups = new ArrayList<>();

                        JSONArray posts = root.getJSONArray("posts");

                        for (int postIndex = 0; postIndex < posts.length(); postIndex++) {
                            JSONObject post = posts.getJSONObject(postIndex);

                            Writeup writeup = new Writeup();
                            writeup.Id = post.getLong("id");
                            writeup.Nick = post.getString("username");

                            writeup.Time = BasePoco.timeFromString(post.getString("inserted_at"));

                            writeup.Content = post.getString("content");
                            writeup.Unread = post.has("new") && post.getBoolean("new");
                            writeup.Rating = post.has("rating") ? post.getInt("rating") : 0;
                            writeup.Type = Writeup.TYPE_DEFAULT; // TODO: ked sa dorobi market tak checknut o co ide
                            writeup.Location = UserActivity.fromJson(post);
                            writeup.IsMine = connector.getAuthNick().equalsIgnoreCase(post.getString("username"));
                            writeup.CanDelete = post.has("can_be_deleted") && post.getBoolean("can_be_deleted");
                            writeup.IsReminded = post.has("reminder") && post.getBoolean("reminder");

                            if (writeup.youtubeFix()) {
                                log.warn(String.format("the writeup=%d from discussion=%d contains youtube, fixed.", writeup.Id, result.Id));
                            }

                            result.Writeups.add(writeup);
                        }
                    }

                    context = Context.fromJSONObject(root);
                } catch (Throwable e) {
                    log.error("GetWriteupsTaskWorker", e);
                    throw new NyxException(e);
                }
            }

            return new SuccessResponse<>(result, context);
        }
    }

    public static class SendWriteupTaskWorker extends TaskWorker<WriteupQuery, NullResponse> {
        @Override
        public NullResponse doWork(WriteupQuery input) throws NyxException {
            Connector connector = new Connector(getContext());

            JSONObject json = null;
            WaitingFile waitingFile = null;

            if (input.AttachmentSource != null) {
                HashMap<String, Object> map = new HashMap<>();
                map.put("file", input.AttachmentSource);
                map.put("file_type", "discussion_attachment");
                map.put("id_specific", input.Id);

                json = connector.multipart("PUT", "/file/upload", map);
                waitingFile = WaitingFile.fromJSONObject(json);
            }

            List<NameValuePair> form = new ArrayList<NameValuePair>();
            form.add(new BasicNameValuePair("content", input.Contents));

            json = connector.form("/discussion/" + input.Id + "/send/text", form);
            // TODO: check if the call was successful

            return NullResponse.success();
        }
    }

    public static class RateWriteupTaskWorker extends TaskWorker<WriteupQuery, VotingResponse> {
        @Override
        public VotingResponse doWork(WriteupQuery input) throws NyxException {
            VotingResponse result = new VotingResponse();

            Connector connector = new Connector(getContext());

            String baseUrl = "/discussion/" + input.Id + "/rating/" + input.TempId + "/" + input.VotingType.toString();
            JSONObject root = connector.post(baseUrl);
            if (root == null) {
                throw new NyxException("Json result was null ?");
            } else {
                try {
                    result.CurrentRating = root.getInt("rating");
                    // TODO:
                    result.Result = VotingResult.RATING_CHANGED;
                } catch (Throwable t) {
                    log.error("RatingOverviewTaskWorker", t);
                    throw new NyxException(t);
                }
            }

            return result;
        }
    }

    public static class RatingOverviewTaskWorker extends TaskWorker<WriteupQuery, VotingInfoResult> {
        @Override
        public VotingInfoResult doWork(WriteupQuery input) throws NyxException {
            VotingInfoResult result = new VotingInfoResult();

            Connector connector = new Connector(getContext());

            String baseUrl = "/discussion/" + input.Id + "/rating/" + input.TempId;
            JSONArray root = connector.getArray(baseUrl);
            if (root == null) {
                throw new NyxException("Json result was null ?");
            } else {
                try {
                    for (int voteIndex = 0; voteIndex < root.length(); voteIndex++) {
                        JSONObject vote = root.getJSONObject(voteIndex);

                        String nick = vote.getString("username");
                        String tag = vote.getString("tag");

                        if (tag.equalsIgnoreCase("positive")) {
                            result.Positive++;
                            result.PositiveList.add(nick);
                        } else if (tag.equalsIgnoreCase("negative")) {
                            result.Negative++;
                            result.NegativeList.add(nick);
                        } else {
                            log.trace("wtf vote:" + nick + ", " + tag);
                        }
                    }
                } catch (Throwable t) {
                    log.error("RatingOverviewTaskWorker", t);
                    throw new NyxException(t);
                }
            }

            return result;
        }
    }

    public static class ReminderTaskWorker extends TaskWorker<WriteupQuery, NullResponse> {
        @Override
        public NullResponse doWork(WriteupQuery input) throws NyxException {
            Connector connector = new Connector(getContext());

            String baseUrl = "/discussion/" + input.Id + "/reminder/" + input.TempId + "/" + input.NewState;

            JSONObject root = connector.post(baseUrl);
            if (root == null) {
                throw new NyxException("Json result was null ?");
            }

            return NullResponse.success();
        }
    }

    public static class DeleteTaskWorker extends TaskWorker<WriteupQuery, NullResponse> {
        @Override
        public NullResponse doWork(WriteupQuery input) throws NyxException {
            Connector connector = new Connector(getContext());

            String baseUrl = "/discussion/" + input.Id + "/delete/" + input.TempId;
            JSONObject root = connector.delete(baseUrl);
            if (root == null) {
                throw new NyxException("Json result was null ?");
            }

            return NullResponse.success();
        }
    }

    public static class GetHomeTaskWorker extends TaskWorker<WriteupQuery, WriteupHomeResponse> {
        @Override
        public WriteupHomeResponse doWork(WriteupQuery input) throws NyxException {
            StringBuilder sb = new StringBuilder();
            WriteupHomeResponse result = new WriteupHomeResponse();

            Connector connector = new Connector(getContext());

            String baseUrl = "/discussion/" + input.Id;
            JSONObject root = connector.get(baseUrl);
            if (root == null) {
                throw new NyxException("Json result was null ?");
            } else {
                try {
                    if (root.has("discussion_common") && root.getJSONObject("discussion_common").has("discussion_specific_data")) {
                        JSONObject discussionCommon = root.getJSONObject("discussion_common");
                        JSONObject discussionSpecific = discussionCommon.getJSONObject("discussion_specific_data");
                        if (discussionSpecific.has("header")) {
                            JSONArray headers = discussionSpecific.getJSONArray("header");
                            for (int headerIndex = 0; headerIndex < headers.length(); headerIndex++) {
                                JSONObject header = headers.getJSONObject(headerIndex);
                                sb.append(header.getString("content"));
                                sb.append("<br/>"); // hehe
                            }
                        }
                    }
                } catch (Throwable t) {
                    log.error("RatingOverviewTaskWorker", t);
                    throw new NyxException(t);
                }
            }

            result.Header = sb.toString();
            return result;
        }
    }

    public static class BookOrUnbookWriteupTaskWorker extends TaskWorker<WriteupBookmarkQuery, WriteupBookmarkResponse> {
        @Override
        public WriteupBookmarkResponse doWork(WriteupBookmarkQuery input) throws NyxException {
            WriteupBookmarkResponse result = new WriteupBookmarkResponse();

            // /api/discussion/1/bookmark?new_state=true&category=5
            Connector connector = new Connector(getContext());

            boolean book = input.QueryType == WriteupBookmarkQueryType.BOOK;
            String baseUrl = "/discussion/" + input.DiscussionId + "/bookmark?new_state=" + book;
            if (book) {
                baseUrl += "&category=" + input.CategoryId;
            }

            JSONObject root = connector.get(baseUrl);

            result.Booked = book;

            return result;
        }
    }
}

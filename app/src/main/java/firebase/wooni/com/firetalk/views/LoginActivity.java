package firebase.wooni.com.firetalk.views;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ProgressBar;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.*;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.*;
import firebase.wooni.com.firetalk.R;
import firebase.wooni.com.firetalk.models.User;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity {

    private View mProgressView;

    private SignInButton mSignInbtn;

    private GoogleApiClient mGoogleAPIClient;

    private GoogleSignInOptions mGoogleSignInoptions;

    private FirebaseAuth mAuth;

    private static int GOOGLE_LOGIN_OPEN = 100;

    private FirebaseAnalytics mFirebaseAnalytics;

    private FirebaseDatabase mDatabase;

    private DatabaseReference mUserRef;

    static {
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        mProgressView = (ProgressBar) findViewById(R.id.login_progress);
        mSignInbtn = (SignInButton) findViewById(R.id.google_sign_in_btn);
        mAuth = FirebaseAuth.getInstance();
        if ( mAuth.getCurrentUser() != null ) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }
        mDatabase = FirebaseDatabase.getInstance();
        mUserRef = mDatabase.getReference("users");
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        GoogleSignInOptions mGoogleSignInoptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        // [END config_signin]

        mGoogleAPIClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        // 실패 시 처리 하는 부분.
                    }
                })
                .addApi(Auth.GOOGLE_SIGN_IN_API, mGoogleSignInoptions)
                .build();

        mSignInbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signIn();
            }
        });
    }

    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleAPIClient);
        startActivityForResult(signInIntent, GOOGLE_LOGIN_OPEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GOOGLE_LOGIN_OPEN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = result.getSignInAccount();
                firebaseAuthWithGoogle(account);
            } else {
                // Google Sign In failed, update UI appropriately
                // ...
            }
        }
    }


    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Snackbar.make(mProgressView, "call onFailuer", Snackbar.LENGTH_LONG).show();
                mDatabase.getReference("error/").setValue(e);
                mDatabase.getReference("error/message").setValue(e.getMessage());
                FirebaseCrash.report(e);
            }
        }).addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {

                if (task.isComplete()) {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = task.getResult().getUser();
                        final User user = new User();
                        user.setEmail(firebaseUser.getEmail());
                        user.setName(firebaseUser.getDisplayName());
                        user.setUid(firebaseUser.getUid());
                        if ( firebaseUser.getPhotoUrl() != null )
                            user.setProfileUrl(firebaseUser.getPhotoUrl().toString());

                        mUserRef.child(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                if ( !dataSnapshot.exists() ) {
                                    mUserRef.child(user.getUid()).setValue(user, new DatabaseReference.CompletionListener() {
                                        @Override
                                        public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {

                                            if ( databaseError == null ) {
                                                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                                finish();
                                            }
                                        }
                                    });
                                } else {
                                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                    finish();
                                }

                                Bundle eventBundle = new Bundle();
                                eventBundle.putString("email", user.getEmail());
                                mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, eventBundle);
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });

                    } else {
                        Snackbar.make(mProgressView, "로그인에 실패하였습니다.", Snackbar.LENGTH_LONG).show();
                    }
                } else {
                    Snackbar.make(mProgressView, "로그인에 실패하였습니다.", Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }
}


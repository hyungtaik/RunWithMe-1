<template>
  <div
    class="auth-layout-wrap"
    :style="{ backgroundImage: 'url(' + bgImage + ')' }">
    <div class="auth-content">
      <div class="card o-hidden">
        <div class="row">
          <div class="col-md-6">
            <div class="p-4">
              <div class="auth-logo text-center mb-30">
                <img :src="logo"/>
              </div>
              <h1 style="text-align:center" class="mb-3 text-18"><code class="mb-3 text-18">R</code>un <code class="mb-3 text-18">W</code>ith <code class="mb-3 text-18">M</code>e</h1>
              <b-form @submit.prevent="formSubmit">
                <b-form-group label="Email Address" class="text-12">
                  <b-form-input
                    class="form-control-rounded"
                    type="text"
                    v-model="userEmail"
                    userEmail
                    required
                  ></b-form-input>
                </b-form-group>

                <b-form-group label="Password" class="text-12">
                  <b-form-input
                    class="form-control-rounded"
                    type="password"
                    v-model="userPw"
                  ></b-form-input>
                </b-form-group>

                <!-- <b-button block to="/" variant="primary btn-rounded mt-2"
                  >Sign In</b-button
                > -->
                <b-button
                  type="submit"
                  tag="button"
                  class="btn-rounded btn-block mt-2"
                  variant="primary mt-2"
                  :disabled="loading">
                  로그인
                </b-button>
                <div v-once class="typo__p" v-if="loading">
                  <div class="spinner sm spinner-primary mt-3"></div>
                </div>
                <b-button
                  to="signUp"
                  block
                  variant="primary mt-2"
                  class="btn-rounded"
                  >회원가입</b-button>
              </b-form>

            </div>
          </div>

        </div>
      </div>
    </div>
  </div>
</template>
<script>
import { mapGetters, mapActions,mapMutations } from "vuex";
export default {
  metaInfo: {
    // if no subcomponents specify a metaInfo.title, this title will be used
    title: "로그인",
  },
  data() {
    return {
      userEmail: "",
      userPw: "",
      userId: "",
      bgImage: require("@/assets/images/signin/loginpage1.png"),
      logo: require("@/assets/images/runnings/loading2.gif"),
      signInImage: require("@/assets/images/photo-long-3.jpg"),
    };
  },
   mounted() {
     this.setLoading();
     this.setLogout();
     this.$store.subscribe((mutation,state) =>{
      if(mutation.type =="mutateAuth"){
        //console.log("바껴써용")
        setTimeout(() => {
          this.$router.go(0)
        },400);
      }
     })

     this.makeVariantToast('info')
  },
  computed: {
    validation() {
      return this.userId.length > 4 && this.userId.length < 13;
    },
    ...mapGetters(["userInfo", "loading", "error"]),
  },

  methods: {
    ...mapActions(["login","signOut"]),
    ...mapMutations(["setLoading","setLogout"]),
     //   toast-target
     makeVariantToast(variant = null) {
      this.$bvToast.toast("모바일로 이용하시는 것을 권장합니다", {
        title: `RWM`,
        variant: variant,
        solid: true,
        // appendToast:'b-toaster-top-center'
      });
    },
    formSubmit() {
      this.login({ userEmail: this.userEmail, userPw: this.userPw });
    },
    makeToast(variant = null, msg) {
      this.$bvToast.toast(msg, {
        title: ` ${"로그인 실패!" || "default"}`,
        variant: variant,
        solid: true,
      });
    },
  },
  watch: {
    error(val) {
      if (val != null) {
        this.makeToast("danger", "이메일과 비밀번호가 일치하지 않습니다.");
      }
    },
  },
};
</script>

<style scoped>
.spinner.sm {
  height: 2em;
  width: 2em;
}
@media (min-width: 768px){
  .col-md-6 {
      -webkit-box-flex: 0;
      flex: auto !important;
      max-width: 100% !important;
  }
}
</style>
